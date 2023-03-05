package io.github.saucam.shiva.hnsw

import HnswIndexBuilder.DEFAULT_EF
import HnswIndexBuilder.DEFAULT_EF_CONSTRUCTION
import HnswIndexBuilder.DEFAULT_M
import io.github.saucam.shiva.common.DistanceCalculator

case class HnswIndexBuilder[V](
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
  def withM(m: Int): HnswIndexBuilder[V] = this.copy(m = m)

  /**
   * @param ef: The size of list of nearest neighbours to the search vector created during
   * search at each layer. Higher ef leads to more accurate but slower search.
   * @return
   */
  def withEf(ef: Int): HnswIndexBuilder[V] = this.copy(ef = ef)

  /**
   * @param efConstruction: The size of the list of nearest neighbours to the insertion vector
   * created during index construction at the insertion layer. Higher efConstruction value
   * leads to longer construction time, but better index quality.
   * Note that increasing efConstruction beyond a certain point does not improve index quality
   * because only M links are chosen to be maintained in the layer from amongst the list of
   * nearest neighbours of size efConstruction.
   * @return
   */
  def withEfConstruction(efConstruction: Int): HnswIndexBuilder[V] = this.copy(efConstruction = efConstruction)

  def build(): HnswIndex[V] =
    new HnswIndex[V](
      dimensions = this.dimensions,
      maxItemCount = this.maxItemCount,
      m = this.m,
      maxM = this.m,
      maxM0 = this.m * 2,
      mL = 1 / Math.log(this.m),
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
