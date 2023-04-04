package io.github.saucam.shiva.hnsw

import io.github.saucam.shiva.common.DistanceCalculator
import io.github.saucam.shiva.common.Item

import HnswIndexBuilder.DEFAULT_EF
import HnswIndexBuilder.DEFAULT_EF_CONSTRUCTION
import HnswIndexBuilder.DEFAULT_M

/**
 * Builder for HnswIndex
 * @param dimensions: The dimensionality of the vectors to be indexed
 * @param maxItemCount: The maximum number of items that can be indexed
 * @param m: The number of bi-directional links created for every new element during construction of the hnsw index.
 * @param ef: The size of list of nearest neighbours to the search vector created during search at each layer.
 * @param efConstruction: The size of list of nearest neighbours to the search vector created during construction at each layer.
 * @param distanceCalculator: The distance calculator to be used for calculating the distance between 2 vectors.
 * @param ordering: The ordering to be used for comparing the distances between 2 vectors.
 * @tparam TId: The type of the id of the item to be indexed (e.g. String, Int, Long)
 * @tparam V: The type of the vector to be indexed (e.g. DenseVector[Double], DenseVector[Float], DenseVector[Int]).
 * @tparam I: The type of the item to be indexed.
 */
case class HnswIndexBuilder[TId, V: Ordering, I <: Item[TId, V]](
    dimensions: Int,
    maxItemCount: Int,
    m: Int = DEFAULT_M,
    ef: Int = DEFAULT_EF,
    efConstruction: Int = DEFAULT_EF_CONSTRUCTION,
    distanceCalculator: DistanceCalculator[V]
) {

  /**
   * @param m: The number of bi-directional links created for every new element during
   * construction of the hnsw index.
   * Higher m works better for high dimensional data and/or high recall
   * Lower m works better for low dimensional data and/or low recalls. This param
   * also directly affects the memory consumption.
   * Note that as more veertices (vectors) are inserted, more links can be added
   * in the lower layers, upto Mmax for layer1 and Mmax0 for layer 0.
   * @return
   */
  def withM(m: Int): HnswIndexBuilder[TId, V, I] = this.copy(m = m)

  /**
   * @param ef: The size of list of nearest neighbours to the search vector created during
   * search at each layer. Higher ef leads to more accurate but slower search.
   * @return
   */
  def withEf(ef: Int): HnswIndexBuilder[TId, V, I] = this.copy(ef = ef)

  /**
   * @param efConstruction: The size of the list of nearest neighbours to the insertion vector
   * created during index construction at the insertion layer. Higher efConstruction value
   * leads to longer construction time, but better index quality.
   * Note that increasing efConstruction beyond a certain point does not improve index quality
   * because only M links are chosen to be maintained in the layer from amongst the list of
   * nearest neighbours of size efConstruction.
   * @return
   */
  def withEfConstruction(efConstruction: Int): HnswIndexBuilder[TId, V, I] = this.copy(efConstruction = efConstruction)

  def build(): HnswIndex[TId, V, I] =
    new HnswIndex[TId, V, I](
      dimensions = this.dimensions,
      maxItemCount = this.maxItemCount,
      m = this.m,
      maxM = this.m,
      maxM0 = this.m * 2,
      mL = 1 / Math.log(this.m.toDouble),
      efConstruction = this.efConstruction,
      ef = this.ef,
      distanceCalculator = this.distanceCalculator
    )
}

object HnswIndexBuilder {

  val DEFAULT_M = 10
  val DEFAULT_EF = 10

  val DEFAULT_EF_CONSTRUCTION = 200
}
