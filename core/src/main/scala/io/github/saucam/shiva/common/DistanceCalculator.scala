package io.github.saucam.shiva.common

import breeze.linalg._

/**
 * Trait that defines the distance calculation between 2 vectors
 *
 * @tparam T: The type elements in the vectors and hence the type of distance
 */
trait DistanceCalculator[@specialized(Int, Double, Float) T] {

  def computeDistance(u: Vector[T], v: Vector[T]): T

}
