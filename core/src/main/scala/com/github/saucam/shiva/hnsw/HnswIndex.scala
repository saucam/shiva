package com.github.saucam.shiva.hnsw

import com.github.saucam.shiva.common.DistanceCalculator
import it.unimi.dsi.fastutil.ints.{IntArrayList, IntOpenHashSet}

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
) {

  // Lookup from vector id to node id that contains the vector
  private val lookup: IntOpenHashSet = new IntOpenHashSet()
  // Store the vector reference based on its id
  private val vectors: Array[Vector[V]] = new Array[Vector[V]](maxItemCount)
  // Connections of each vector at each level
  private val connections: Array[Array[IntArrayList]] = new Array[Array[IntArrayList]](maxItemCount)

  def size(): Int = lookup.size

  def get(id: Int): Option[Vector[V]] = {
    if (lookup contains id) {
      Option(vectors(id))
    } else None
  }
}

object HnswIndex {
  def apply[V](builder: HnswIndexBuilder[V]): HnswIndex[V] =
    builder.build()
}
