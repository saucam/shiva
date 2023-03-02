package com.github.saucam.shiva.hnsw

import org.scalatest.Inspectors
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import com.github.saucam.shiva.common.EuclideanDistanceDouble

class HnswIndexSpec extends AnyFunSuite with Matchers with Inspectors {

  def bincount(arr: Array[Int]): Array[Int] = {
    val counts = scala.collection.mutable.Map[Int, Int]().withDefaultValue(0)
    for (x <- arr) counts(x) += 1
    val maxVal = arr.maxOption.getOrElse(0)
    val result = new Array[Int](maxVal + 1)
    for ((k, v) <- counts) result(k) = v
    result
  }

  test("Random level generation will assign levels following an exponential distribution") {
    val index = HnswIndexBuilder[Double](
      dimensions = 10,
      maxItemCount = 1000000,
      m = 32,
      distanceCalculator = new EuclideanDistanceDouble
    ).build()
    val genLevels = (0 to 1000000).map(_ => index.randomLevel()).toArray
    val binCount = bincount(genLevels)
    binCount should equal(binCount.sorted.reverse)
    binCount(0) should be > 100000
    binCount(1) should be < 100000
    binCount(1) should be > 10000
    binCount(2) should be < 10000
    binCount(2) should be > 100
  }
}
