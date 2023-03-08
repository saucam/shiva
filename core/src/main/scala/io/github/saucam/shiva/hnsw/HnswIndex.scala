package io.github.saucam.shiva.hnsw

import scala.annotation.tailrec
import io.github.saucam.shiva.Index
import io.github.saucam.shiva.common.{DistanceCalculator, Item, Node}
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import HnswIndex.INVALID_ID
import io.github.saucam.shiva.exception.SizeLimitExceededException
import it.unimi.dsi.fastutil.objects.{Object2IntMap, Object2IntOpenHashMap}

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
) extends Index[TId, I] {

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
  @volatile private var entryPoint: Node[TId, V, I] = null

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

    if (currObj != null) {

      if (newNode.maxLevel() < epCopy.maxLevel()) {

        var curDist = distanceCalculator.computeDistance(v.vector(), currObj.item.vector())

        (epCopy.maxLevel() to newNode.maxLevel() by -1) foreach { activeLevel =>
          var changed = true

          while (changed) {
            changed = false

            val candidateConnections = currObj.connections(activeLevel)

            import scala.jdk.CollectionConverters._
            candidateConnections.asScala.foreach { candidateId =>
              val candidateNode = nodes(candidateId)
              val candidateDistance = distanceCalculator.computeDistance(v.vector(), candidateNode.item.vector())
              if (candidateDistance < curDist) {
                curDist = candidateDistance
                currObj = candidateNode
                changed = true
              }

            }
          }
        }
      }

      (Math.min(rndLevel, epCopy.maxLevel()) to 0 by -1) foreach { level =>
      }

    }

    // TODO: Complete this function
    false
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

  def apply[V](builder: HnswIndexBuilder[V]): HnswIndex[V] =
    builder.build()
}
