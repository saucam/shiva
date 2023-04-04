package io.github.saucam.shiva.common

import scala.{specialized => spec}

import breeze.linalg._

/**
 * Type of Item to be indexed
 * @tparam I: type of the unique identifier of the item.
 * @tparam V: type of the elements in the vector.
 */
trait Item[I, @spec(Int, Double, Float) V] extends Serializable {

  /**
   * Returns the unique identifier of this item.
   * @return
   */
  val id: I

  /**
   * Returns the actual vector that will be indexed when this item is
   * added to the index.
   * @return
   */
  val vector: Vector[V]

  /**
   * Returns the dimensionality of the vector.
   * @return
   */
  def dimension(): Int

}
