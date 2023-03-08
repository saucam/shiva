package io.github.saucam.shiva.hnsw

import scala.annotation.tailrec

import io.github.saucam.shiva.Index
import io.github.saucam.shiva.common.DistanceCalculator
import io.github.saucam.shiva.common.Node
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList

import HnswIndex.INVALID_ID

class HnswIndex[V](
    dimensions: Int,
    maxItemCount: Int,
    m: Int,
    maxM: Int,
    maxM0: Int,
    ef: Int,
    efConstruction: Int,
    mL: Double,
    distanceCalculator: DistanceCalculator[V]
) extends Index[Vector[V]] {

  // Probability of insertion at a given layer
  type LevelProb = Array[Double]
  // Cumulative total of nearest neighbours assigned to a vertex at
  // different insertion levels
  type LevelDegree = Array[Int]

  // Pre-compute some DS
  val (cumulativeNumNNPerLevel, insertionProbPerLevel) = setDefaultProbas(m, mL)

  // Lookup from vector id to node id that contains the vector
  private val lookup: Int2IntOpenHashMap = new Int2IntOpenHashMap()
  // Store the node reference based on its id
  private val nodes: Array[Node[Vector[V]]] = new Array[Node[Vector[V]]](maxItemCount)

  // RNG for assigning levels
  private val rand = scala.util.Random
  rand.setSeed(1313131)

  override def size(): Int = lookup.size

  override def get(id: Int): Option[Vector[V]] = {
    if (lookup containsKey id) {
      val nodeId = lookup.get(id)
      Option(nodes(nodeId).item)
    } else None
  }

  override def contains(id: Int): Boolean = lookup containsKey id

  override def add(v: Vector[V]): Int = {
    if (v.length != dimensions) {
      return INVALID_ID
    }

    val rndLevel = randomLevel()
    val connections: Array[IntArrayList] = new Array[IntArrayList](rndLevel + 1)

    (0 to rndLevel).foreach { level =>
      val levelM = if (rndLevel == 0) maxM0 else maxM
      connections(level) = new IntArrayList(levelM)
    }

    // TODO: Complete this function
    INVALID_ID
  }

  override def addAll(iterV: Iterable[Vector[V]]): Iterable[Int] = ???

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
