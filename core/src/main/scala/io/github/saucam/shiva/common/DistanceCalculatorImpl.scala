package io.github.saucam.shiva.common

import scala.math.pow

import breeze.linalg._
import breeze.numerics.abs

class InnerProductFloat extends DistanceCalculator[Float] {
  override def computeDistance(u: Vector[Float], v: Vector[Float]): Float = 1 - (u dot v)
}

class InnerProductDouble extends DistanceCalculator[Double] {
  override def computeDistance(u: Vector[Double], v: Vector[Double]): Double = 1 - (u dot v)
}

/**
 * When normalized, euclidean distance between 2 vectors x, y is equal to 1 - (x y)
 */
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

class CosineSimilarityFloat extends DistanceCalculator[Float] {
  override def computeDistance(u: Vector[Float], v: Vector[Float]): Float = {
    val dotProduct = u dot v
    val normU = norm(u)
    val normV = norm(v)
    val cosineSimilarity = dotProduct / (normU * normV)
    1 - cosineSimilarity.toFloat
  }
}

class CosineSimilarityDouble extends DistanceCalculator[Double] {
  override def computeDistance(u: Vector[Double], v: Vector[Double]): Double = {
    val dotProduct = u dot v
    val normU = norm(u)
    val normV = norm(v)
    val cosineSimilarity = dotProduct / (normU * normV)
    1 - cosineSimilarity
  }
}

class ManhattanDistanceFloat extends DistanceCalculator[Float] {
  override def computeDistance(u: Vector[Float], v: Vector[Float]): Float = {
    val diff = u - v
    sum(abs(diff))
  }
}

class ManhattanDistanceDouble extends DistanceCalculator[Double] {
  override def computeDistance(u: Vector[Double], v: Vector[Double]): Double = {
    val diff = u - v
    sum(abs(diff))
  }
}

class MinkowskiDistanceFloat(p: Int) extends DistanceCalculator[Float] {
  override def computeDistance(u: Vector[Float], v: Vector[Float]): Float = {
    val diff = u - v
    val absDiff = abs(diff)
    val powDiff = absDiff.mapValues(d => pow(d.toDouble, p.toDouble))
    val sumDiff = sum(powDiff)
    pow(sumDiff, 1.0 / p).toFloat
  }
}

class MinkowskiDistanceDouble(p: Int) extends DistanceCalculator[Double] {
  override def computeDistance(u: Vector[Double], v: Vector[Double]): Double = {
    val diff = u - v
    val absDiff = abs(diff)
    val powDiff = absDiff.mapValues(d => pow(d, p.toDouble))
    val sumDiff = sum(powDiff)
    pow(sumDiff, 1.0 / p)
  }
}
