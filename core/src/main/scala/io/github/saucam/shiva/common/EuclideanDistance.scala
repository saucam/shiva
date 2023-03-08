package io.github.saucam.shiva.common

import breeze.linalg._

/**
 * When normalized, euclidean distance between 2 vectors x, y is equal to 1 - (x y)
 */
class InnerProductFloat extends DistanceCalculator[Float] {
  override def computeDistance(u: Vector[Float], v: Vector[Float]): Float = 1 - (u dot v)
}

class InnerProductDouble extends DistanceCalculator[Double] {
  override def computeDistance(u: Vector[Double], v: Vector[Double]): Double = 1 - (u dot v)
}

class EuclideanDistanceFloat extends DistanceCalculator[Float] {
  override def computeDistance(u: Vector[Float], v: Vector[Float]): Float = {
    val b = v * -1f
    val diff = u + b
    val dot = diff dot diff
    Math.sqrt(dot.toDouble).toFloat
  }
}

class EuclideanDistanceDouble extends DistanceCalculator[Double] {
  override def computeDistance(u: Vector[Double], v: Vector[Double]): Double = {
    val b = v * -1d
    val diff = u + b
    Math.sqrt(diff dot diff)
  }
}
