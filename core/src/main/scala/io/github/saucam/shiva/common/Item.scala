package io.github.saucam.shiva.common

import breeze.linalg._
import scala.{specialized => spec}

trait Item[I, @spec(Int, Double, Float) V] extends Serializable {

  /**
   * Returns the unique identifier of this item.
   * @return
   */
  def id(): I

  /**
   * Returns the actual vector that will be indexed when this item is
   * added to the index.
   * @return
   */
  def vector(): Vector[V]

  /**
   * Returns the dimensionality of the vector.
   * @return
   */
  def dimension(): Int

}
