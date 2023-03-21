package io.github.saucam.shiva.hnsw

import scala.annotation.tailrec
import io.github.saucam.shiva.{Index, SearchResult}
import io.github.saucam.shiva.common.{DistanceCalculator, Item, Node}
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import HnswIndex.INVALID_ID
import io.github.saucam.shiva.exception.SizeLimitExceededException
import it.unimi.dsi.fastutil.objects.{Object2IntMap, Object2IntOpenHashMap}
import breeze.linalg._
import spire.implicits.metricSpaceOps

import scala.collection.{BitSet, mutable}
import scala.collection.mutable.ListBuffer
import scala.math.Ordered.orderingToOrdered
import scala.{specialized => spec}

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
) extends Index[TId, V, I] {

  // Probability of insertion at a given layer
  type LevelProb = Array[Double]
  // Cumulative total of nearest neighbours assigned to a vertex at
  // different insertion levels
  type LevelDegree = Array[Int]

  // Pre-compute some DS
  val (cumulativeNumNNPerLevel, insertionProbPerLevel) = setDefaultProbas(m, mL)

  // Lookup from vector id to node id that contains the vector
  private val lookup: Object2IntOpenHashMap[TId] = new Object2IntOpenHashMap[TId]()
  // Store the node reference based on its id
  private val nodes: Array[Node[TId, V, I]] = new Array[Node[TId, V, I]](maxItemCount)

  private var nodeCount: Int = 0
  // Reference to entryPoint node
  @volatile private var entryPoint: Option[Node[TId, V, I]] = None

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
      return false
    }

    val rndLevel = randomLevel()
    val connections: Array[IntArrayList] = new Array[IntArrayList](rndLevel + 1)

    (0 to rndLevel).foreach { level =>
      val levelM = if (rndLevel == 0) maxM0 else maxM
      connections(level) = new IntArrayList(levelM)
    }

    if (lookup containsKey v.id()) {

      val nodeId = lookup.getInt(v.id())

      val node = nodes(nodeId)

      // Case of re-insertion, simply return true
      if (node.item.vector() equals v.vector()) {
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
    val newNodeId = nodeCount + 1
    nodeCount += 1

    val newNode = Node[TId, V, I](newNodeId, v, connections)
    nodes(newNodeId) = newNode

    // Store the item id to nodeId mapping
    lookup.put(v.id(), newNodeId)

    // Run a nearest neighbour search across levels, and make connections
    val epCopy = entryPoint

    var currObj = epCopy

    if (currObj != None) {

      if (newNode.maxLevel() < epCopy.get.maxLevel()) {

        var curDist = distanceCalculator.computeDistance(v.vector(), currObj.get.item.vector())

        (epCopy.get.maxLevel() to newNode.maxLevel() by -1) foreach { activeLevel =>
          var changed = true

          while (changed) {
            changed = false

            val candidateConnections = currObj.get.connections(activeLevel)

            import scala.jdk.CollectionConverters._
            candidateConnections.asScala.foreach { candidateId =>
              val candidateNode = nodes(candidateId)
              val candidateDistance = distanceCalculator.computeDistance(v.vector(), candidateNode.item.vector())
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
        val topCandidates = searchBaseLayer(currObj.get, v.vector(), efConstruction, level)
        connectNewNode(level, newNode, topCandidates)
      }
    }

    if (entryPoint.isEmpty || newNode.maxLevel() > epCopy.get.maxLevel()) {
      this.entryPoint = Option(newNode)
    }

    // TODO: Complete this function
    true
  }

  case class NodeWithDistance(nodeId: Int, distance: V)

  import scala.jdk.CollectionConverters._

  def connectNewNode(
      level: Int,
      node: Node[TId, V, I],
      topCandidatesP: mutable.PriorityQueue[NodeWithDistance]
  ): Unit = {
    val bestN = if (level == 0) maxM0 else maxM

    val newNodeId = node.id
    val newItemVector = node.item.vector()
    val newItemConnections = node.connections(level)

    val topCandidates = getNeighborsByHeuristic(topCandidatesP, m)

    topCandidates
      .toList
//      .filterNot { selectedNeighbour =>
//        excludedCandidates.synchronized {
//          excludedCandidates.contains(selectedNeighbour.nodeId)
//        }
//      }
      .foreach { selectedNeighbour =>
        val selectedNeighbourId = selectedNeighbour.nodeId

        newItemConnections.add(selectedNeighbourId)

        val neighbourNode = nodes(selectedNeighbourId)

        val neighbourVector = neighbourNode.item.vector()
        val neighbourConnectionsAtLevel = neighbourNode.connections(level)

        if (neighbourConnectionsAtLevel.size < bestN) {
          neighbourConnectionsAtLevel.add(newNodeId)
        } else {
          val dMax = distanceCalculator.computeDistance(newItemVector, neighbourVector)

          val candidates = new mutable.PriorityQueue()(Ordering.by((nd: NodeWithDistance) => nd.distance).reverse)
          candidates.addOne(NodeWithDistance(newNodeId, dMax))

          val it = neighbourConnectionsAtLevel.iterator()
          while (it.hasNext) {
            val id = it.nextInt()
            val dist = distanceCalculator.computeDistance(neighbourVector, nodes(id).item.vector())
            candidates.addOne(NodeWithDistance(id, dist))
          }

          val nCandidates = getNeighborsByHeuristic(candidates, bestN)
          neighbourConnectionsAtLevel.clear()
          val it2 = nCandidates.iterator.map(_.nodeId)

          while (it2.hasNext) {
            neighbourConnectionsAtLevel.add(it2.next())
          }
        }
      }
  }

  private def getNeighborsByHeuristic(
      topCandidates: mutable.PriorityQueue[NodeWithDistance],
      m: Int
  ): mutable.PriorityQueue[NodeWithDistance] = {
    if (topCandidates.size < m) {
      return topCandidates
    }

    val queueClosest =
      mutable.PriorityQueue.from(topCandidates.toList)(Ordering.by((nd: NodeWithDistance) => nd.distance))
    val returnList = queueClosest
      .foldLeft(List.empty[NodeWithDistance]) { (acc, currentPair) =>
        if (acc.size >= m) {
          acc
        } else {
          val distToQuery = currentPair.distance
          val good = acc.forall { secondPair =>
            val curdist = distanceCalculator.computeDistance(
              nodes(secondPair.nodeId).item.vector(),
              nodes(currentPair.nodeId).item.vector()
            )
            !(curdist < distToQuery)
          }
          if (good) {
            currentPair :: acc
          } else {
            acc
          }
        }
      }
      .reverse

    topCandidates.clear()
    topCandidates.addAll(returnList)
  }

  def searchBaseLayer(
      entryPointNode: Node[TId, V, I],
      destination: Vector[V],
      k: Int,
      layer: Int
  ): mutable.PriorityQueue[NodeWithDistance] = {

    val visitedBitSet = mutable.BitSet() // visitedBitSetPool.borrowObject()
    try {
      val topCandidates = mutable.PriorityQueue.empty(Ordering.by((nd: NodeWithDistance) => nd.distance).reverse)
      val candidateSet =
        mutable.PriorityQueue.empty[NodeWithDistance](Ordering.by((nd: NodeWithDistance) => nd.distance))

      val distance = distanceCalculator.computeDistance(destination, entryPointNode.item.vector())
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
            val candidateDistance = distanceCalculator.computeDistance(destination, candidateNode.item.vector())

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
        var curDist = distanceCalculator.computeDistance(destination, currObj.item.vector())
        // distanceFunction(destination, currObj.item.vector())

        for (activeLevel <- entryPointCopy.maxLevel() to 1 by -1) {
          var changed = true

          while (changed) {
            changed = false

            val candidateConnections = currObj.connections(activeLevel)

            for (i <- 0 until candidateConnections.size) {
              val candidateId = candidateConnections.getInt(i)

              val candidateDistance =
                distanceCalculator.computeDistance(destination, nodes(candidateId).item.vector())
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

        val results = List.fill(topCandidates.size) {
          val pair = topCandidates.dequeue()
          SearchResult(nodes(pair.nodeId).item.id(), pair.distance)
        }

        results
    }
  }

  override def addAll(iterV: Iterable[I]): Iterable[Int] = ???

  private def probabilityAtLevel(level: Int, mL: Double): Double =
    Math.exp(-level / mL) * (1 - Math.exp(-1 / mL))

  def probs(level: Int, mL: Double): LazyList[Double] =
    LazyList.cons(probabilityAtLevel(level, mL), probs(level + 1, mL))

  def setDefaultProbas(m: Int, mL: Double): (LevelDegree, LevelProb) = {
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

  val INVALID_ID = -1

  def apply[TId, @spec(Int, Double, Float) V: Ordering, I <: Item[TId, V]](builder: HnswIndexBuilder[TId, V, I])
      : HnswIndex[TId, V, I] =
    builder.build()
}
