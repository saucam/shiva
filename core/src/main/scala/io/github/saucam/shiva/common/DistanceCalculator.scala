package io.github.saucam.shiva.common

import breeze.linalg._

trait DistanceCalculator[@specialized(Int, Double, Float) T] {

  def computeDistance(u: Vector[T], v: Vector[T]): T

}
