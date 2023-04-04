package io.github.saucam.shiva.hnsw

import scala.annotation.tailrec
import scala.collection.mutable
import scala.math.Ordered.orderingToOrdered
import scala.{specialized => spec}

import breeze.linalg._
import io.github.saucam.shiva.ItemIndex
import io.github.saucam.shiva.SearchResult
import io.github.saucam.shiva.common.DistanceCalculator
import io.github.saucam.shiva.common.Item
import io.github.saucam.shiva.common.Node
import io.github.saucam.shiva.exception.SizeLimitExceededException
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap

/**
 * Implementation of Hierarchical Navigable Small World Graphs (HNSW) as described in
 * the paper by Yu A. Malkov, D. A. Yashunine (2018) https://arxiv.org/abs/1603.09320
 * @param dimensions: number of dimensions of the vectors to be indexed
 * @param maxItemCount: maximum number of items that can be added to the index
 * @param m: number of nearest neighbours to be assigned to a newly inserted node
 * @param maxM: maximum number of outgoing connections from a node
 * @param maxM0: maximum number of outgoing connections from the entry point
 * @param ef: number of nearest neighbours to consider during the search
 * @param efConstruction: number of nearest neighbours to consider during the construction
 * @param mL: level multiplier
 * @param distanceCalculator: distance calculator to be used
 * @param ordering: ordering for the type V
 * @tparam TId: type of the id of the item to be added to the index
 * @tparam V: type of the vector to be indexed
 * @tparam I: type of item to be added to the index
 */
class HnswIndex[TId, @spec(Int, Double, Float) V: Ordering, I <: Item[TId, V]](
    dimensions: Int,
    maxItemCount: Int,
    m: Int,
    maxM: Int,
    maxM0: Int,
    ef: Int,
    efConstruction: Int,
    mL: Double,
    distanceCalculator: DistanceCalculator[V]
) extends ItemIndex[TId, V, I] {

  // Probability of insertion at a given layer
  private type LevelProb = Array[Double]
  // Cumulative total of nearest neighbours assigned to a vertex at
  // different insertion levels
  private type LevelDegree = Array[Int]

  // Pre-compute some DS
  val (cumulativeNumNNPerLevel, insertionProbPerLevel) = setDefaultProbas(m, mL)

  // Lookup from vector id to node id that contains the vector
  private val lookup: Object2IntOpenHashMap[TId] = new Object2IntOpenHashMap[TId]()
  // Store the node reference based on its id
  private val nodes: Array[Node[TId, V, I]] = new Array[Node[TId, V, I]](maxItemCount)

  private var nodeCount: Int = 0
  // Reference to entryPoint node
  @volatile private var entryPoint: Option[Node[TId, V, I]] = None

  private val excludedCandidates: mutable.BitSet = new mutable.BitSet(maxItemCount)

  // RNG for assigning levels
  private val rand = scala.util.Random
  rand.setSeed(1313131)

  override def size(): Int = lookup.size

  override def get(id: TId): Option[I] = {
    if (lookup containsKey id) {
      val nodeId = lookup.getInt(id)
      Option(nodes(nodeId).item)
    } else None
  }

  override def contains(id: TId): Boolean = lookup containsKey id

  override def add(v: I): Boolean = {
    if (v.dimension() != dimensions) {
      throw new IllegalArgumentException(s"Item does not have dimensionality of: $dimensions")
    }

    val rndLevel = randomLevel()
    val connections: Array[IntArrayList] = Array.fill(rndLevel + 1) {
      val levelM = if (rndLevel == 0) maxM0 else maxM
      new IntArrayList(levelM)
    }

    if (lookup containsKey v.id) {

      val nodeId = lookup.getInt(v.id)

      val node = nodes(nodeId)

      // Case of re-insertion, simply return true
      if (node.item.vector equals v.vector) {
        return true
      } else {
        // Do we want to handle the case of replacement?
        // Currently just say false as item exists
        return false
      }
    }

    if (nodeCount >= maxItemCount) {
      throw SizeLimitExceededException(s"The index is full, total items indexed: ${maxItemCount}")
    }

    // Insert new node with the item to be added
    val newNodeId = nodeCount
    nodeCount += 1

    excludedCandidates.add(newNodeId)

    val newNode = Node[TId, V, I](newNodeId, v, connections)
    nodes(newNodeId) = newNode

    // Store the item id to nodeId mapping
    lookup.put(v.id, newNodeId)

    // Run a nearest neighbour search across levels, and make connections
    val epCopy = entryPoint

    try {

      var currObj = epCopy

      if (currObj.isDefined) {

        if (newNode.maxLevel() < epCopy.get.maxLevel()) {

          var curDist = distanceCalculator.computeDistance(v.vector, currObj.get.item.vector)

          (epCopy.get.maxLevel() to newNode.maxLevel() by -1) foreach { activeLevel =>
            var changed = true

            while (changed) {
              changed = false

              val candidateConnections = currObj.get.connections(activeLevel)

              (0 until candidateConnections.size()) foreach { i =>
                val candidateId = candidateConnections.getInt(i)

                val candidateNode = nodes(candidateId)
                val candidateDistance = distanceCalculator.computeDistance(v.vector, candidateNode.item.vector)
                if (candidateDistance < curDist) {
                  curDist = candidateDistance
                  currObj = Option(candidateNode)
                  changed = true
                }
              }
            }
          }
        }

        (Math.min(rndLevel, epCopy.get.maxLevel()) to 0 by -1) foreach { level =>
          val topCandidates = searchBaseLayer(currObj.get, v.vector, efConstruction, level)
          connectNewNode(level, newNode, topCandidates)
        }
      }

      if (entryPoint.isEmpty || newNode.maxLevel() > epCopy.get.maxLevel()) {
        this.entryPoint = Option(newNode)
      }
      true
    } finally {
      excludedCandidates.remove(newNodeId): Unit
    }
  }

  case class NodeWithDistance(nodeId: Int, distance: V)

  private def connectNewNode(
      level: Int,
      node: Node[TId, V, I],
      topCandidatesP: mutable.PriorityQueue[NodeWithDistance]
  ): Unit = {
    val bestN = if (level == 0) maxM0 else maxM

    val newNodeId = node.id
    val newItemVector = node.item.vector
    val newItemConnections = node.connections(level)

    val topCandidates = getNeighborsByHeuristic(topCandidatesP, m)

    while (topCandidates.nonEmpty) {
      val selectedNeighbour = topCandidates.dequeue()
      val selectedNeighbourId = selectedNeighbour.nodeId

      if (!excludedCandidates.contains(selectedNeighbourId)) {

        newItemConnections.add(selectedNeighbourId)

        val neighbourNode = nodes(selectedNeighbourId)

        val neighbourVector = neighbourNode.item.vector
        val neighbourConnectionsAtLevel = neighbourNode.connections(level)

        if (neighbourConnectionsAtLevel.size < bestN) {
          neighbourConnectionsAtLevel.add(newNodeId)
        } else {
          val dMax = distanceCalculator.computeDistance(newItemVector, neighbourVector)

          val candidates = new mutable.PriorityQueue()(Ordering.by((nd: NodeWithDistance) => nd.distance))
          candidates.addOne(NodeWithDistance(newNodeId, dMax))

          val it = neighbourConnectionsAtLevel.iterator()
          while (it.hasNext) {
            val id = it.nextInt()
            val dist = distanceCalculator.computeDistance(neighbourVector, nodes(id).item.vector)
            candidates.addOne(NodeWithDistance(id, dist))
          }

          val nCandidates = getNeighborsByHeuristic(candidates, bestN)
          neighbourConnectionsAtLevel.clear()

          while (nCandidates.nonEmpty) {
            val c = nCandidates.dequeue()

            neighbourConnectionsAtLevel.add(c.nodeId)
          }
        }
      }
    }
  }

  private def getNeighborsByHeuristic(
      topCandidates: mutable.PriorityQueue[NodeWithDistance],
      m: Int
  ): mutable.PriorityQueue[NodeWithDistance] = {

    @tailrec
    def conditionalLoop(
        returnList: mutable.ListBuffer[NodeWithDistance],
        targetPair: NodeWithDistance,
        index: Int
    ): Boolean = {

      if (index >= returnList.length) {
        return true
      }

      val nextPair = returnList(index)

      val curdist = distanceCalculator.computeDistance(
        nodes(nextPair.nodeId).item.vector,
        nodes(targetPair.nodeId).item.vector
      )

      val distToQuery = targetPair.distance
      if (curdist < distToQuery) {
        return false
      }
      conditionalLoop(returnList, targetPair, index + 1)
    }

    if (topCandidates.size < m) {
      return topCandidates
    }

    val queueClosest =
      mutable.PriorityQueue.empty[NodeWithDistance](Ordering.by((nd: NodeWithDistance) => nd.distance))
    val returnList = new mutable.ListBuffer[NodeWithDistance]()

    while (topCandidates.nonEmpty) {
      queueClosest.addOne(topCandidates.dequeue())
    }

    while (queueClosest.nonEmpty && (returnList.size < m)) {

      val currentPair = queueClosest.dequeue()

      val good = conditionalLoop(returnList, currentPair, 0)

      if (good) {
        returnList += currentPair
      }
    }

    topCandidates.addAll(returnList)
  }

  def searchBaseLayer(
      entryPointNode: Node[TId, V, I],
      destination: Vector[V],
      k: Int,
      layer: Int
  ): mutable.PriorityQueue[NodeWithDistance] = {

    val visitedBitSet = mutable.BitSet()
    try {
      val topCandidates =
        mutable.PriorityQueue.empty[NodeWithDistance](Ordering.by((nd: NodeWithDistance) => nd.distance))
      val candidateSet =
        mutable.PriorityQueue.empty[NodeWithDistance](Ordering.by((nd: NodeWithDistance) => nd.distance).reverse)

      val distance = distanceCalculator.computeDistance(destination, entryPointNode.item.vector)
      var lowerBound = distance

      val pair = NodeWithDistance(entryPointNode.id, distance)
      topCandidates += pair
      lowerBound = distance
      candidateSet += pair

      visitedBitSet.add(entryPointNode.id)

      while (candidateSet.nonEmpty) {
        val currentPair = candidateSet.dequeue()

        if (currentPair.distance > lowerBound) {
          return topCandidates
        }

        val node = nodes(currentPair.nodeId)
        val candidates = node.connections(layer)

        (0 until candidates.size) foreach { i =>
          val candidateId = candidates.getInt(i)
          if (!visitedBitSet.contains(candidateId)) {
            visitedBitSet.add(candidateId)
            val candidateNode = nodes(candidateId)
            val candidateDistance = distanceCalculator.computeDistance(destination, candidateNode.item.vector)

            if (topCandidates.size < k || (lowerBound > candidateDistance)) {
              val candidatePair = NodeWithDistance(candidateId, candidateDistance)
              candidateSet.addOne(candidatePair)

              topCandidates += candidatePair

              if (topCandidates.size > k) {
                topCandidates.dequeue()
              }

              if (topCandidates.nonEmpty) {
                lowerBound = topCandidates.head.distance
              }
            }
          }
        }
      }

      topCandidates
    } finally {
      visitedBitSet.clear()
      // visitedBitSetPool.returnObject(visitedBitSet)
    }
  }

  override def findNearestNeighbors(destination: Vector[V], k: Int): List[SearchResult[TId, V]] = {
    entryPoint match {
      case None => List.empty
      case Some(entryPointCopy) =>
        var currObj = entryPointCopy
        var curDist = distanceCalculator.computeDistance(destination, currObj.item.vector)

        for (activeLevel <- entryPointCopy.maxLevel() to 1 by -1) {
          var changed = true

          while (changed) {
            changed = false

            val candidateConnections = currObj.connections(activeLevel)

            for (i <- 0 until candidateConnections.size) {
              val candidateId = candidateConnections.getInt(i)

              val candidateDistance =
                distanceCalculator.computeDistance(destination, nodes(candidateId).item.vector)
              // distanceFunction(destination, nodes(candidateId).item.vector())
              if (candidateDistance < curDist) {
                curDist = candidateDistance
                currObj = nodes(candidateId)
                changed = true
              }
            }

          }
        }

        val topCandidates = searchBaseLayer(currObj, destination, math.max(ef, k), 0)

        while (topCandidates.size > k) {
          topCandidates.dequeue()
        }

        var results = List.empty[SearchResult[TId, V]]
        while (topCandidates.nonEmpty) {
          val c = topCandidates.dequeue()
          results = SearchResult(nodes(c.nodeId).item.id, c.distance) :: results
        }

        results
    }
  }

  override def addAll(iterV: Iterable[I]): Iterable[Int] = ???

  private def probabilityAtLevel(level: Int, mL: Double): Double =
    Math.exp(-level / mL) * (1 - Math.exp(-1 / mL))

  private def probs(level: Int, mL: Double): LazyList[Double] =
    LazyList.cons(probabilityAtLevel(level, mL), probs(level + 1, mL))

  private def setDefaultProbas(m: Int, mL: Double): (LevelDegree, LevelProb) = {
    val probas = probs(0, mL).takeWhile(proba => proba > 1e-9).toArray
    val nnPerLevel = probas.scanLeft(2 * m) {
      case (cumulative, _) => cumulative + m
    }
    (nnPerLevel, probas)
  }

  /**
   * Generate a random insertion level. Called for each vertex when inserting into the index
   * Key is to return a distribution s.t level 0 ends up with maximum vertices, and extremely
   * few vertices make it to the top layer.
   *
   * @param f
   * @param level
   * @return
   */
  @tailrec
  final def randomLevel(f: Double = rand.nextDouble(), level: Int = 0): Int = {
    if (f < insertionProbPerLevel(level)) {
      return level
    }
    randomLevel(f - insertionProbPerLevel(level), level + 1)
  }
}

object HnswIndex {

  val INVALID_ID: Int = -1

  def apply[TId, @spec(Int, Double, Float) V, I <: Item[TId, V]](builder: HnswIndexBuilder[TId, V, I])
      : HnswIndex[TId, V, I] =
    builder.build()
}
