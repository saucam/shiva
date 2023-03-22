package io.github.saucam.shiva.hnsw

import breeze.linalg._
import io.github.saucam.shiva.SearchResult
import io.github.saucam.shiva.common.EuclideanDistanceDouble
import io.github.saucam.shiva.common.IntDoubleIndexItem
import org.scalatest.Inspectors
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

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
    val index = HnswIndexBuilder[Int, Double, IntDoubleIndexItem](
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

  test("Index size is correctly returned") {
    val index = HnswIndexBuilder[Int, Double, IntDoubleIndexItem](
      dimensions = 3,
      maxItemCount = 1000000,
      m = 32,
      distanceCalculator = new EuclideanDistanceDouble
    ).build()

    index.size() should equal(0)

    index.add(IntDoubleIndexItem(1, Vector(4.05d, 1.06d, 7.8d)))

    index.size() should equal(1)
  }

  test("Can retrieve item from Hnsw index") {
    val index = HnswIndexBuilder[Int, Double, IntDoubleIndexItem](
      dimensions = 3,
      maxItemCount = 1000000,
      m = 32,
      distanceCalculator = new EuclideanDistanceDouble
    ).build()

    val item = IntDoubleIndexItem(1, Vector(4.05d, 1.06d, 7.8d))
    index.get(1) shouldBe Option.empty

    index.add(item)

    index.get(1) should not be Option.empty
    index.get(1) should equal(Option(item))
  }

  test("Adding same item with same id will not add the item again in Hnsw Index") {
    val index = HnswIndexBuilder[Int, Double, IntDoubleIndexItem](
      dimensions = 3,
      maxItemCount = 1000000,
      m = 32,
      distanceCalculator = new EuclideanDistanceDouble
    ).build()

    val item1 = IntDoubleIndexItem(1, Vector(4.05d, 1.06d, 7.8d))

    val res1 = index.add(item1)
    val res2 = index.add(item1)

    res1 should equal(true)
    res2 should equal(true)
    index.size() should equal(1)
  }

  test("Adding another item with same id will not add the item again in Hnsw Index") {
    val index = HnswIndexBuilder[Int, Double, IntDoubleIndexItem](
      dimensions = 3,
      maxItemCount = 1000000,
      m = 32,
      distanceCalculator = new EuclideanDistanceDouble
    ).build()

    val item1 = IntDoubleIndexItem(1, Vector(4.05d, 1.06d, 7.8d))
    val item2 = IntDoubleIndexItem(1, Vector(8.01d, 2.06d, 1.8d))

    val res1 = index.add(item1)
    val res2 = index.add(item2)

    res1 should equal(true)
    res2 should equal(false)
    index.size() should equal(1)
  }

  test("Can retrieve item from itemId from Hnsw Index") {
    val index = HnswIndexBuilder[Int, Double, IntDoubleIndexItem](
      dimensions = 3,
      maxItemCount = 1000000,
      m = 32,
      distanceCalculator = new EuclideanDistanceDouble
    ).build()

    val item = IntDoubleIndexItem(1, Vector(4.05d, 1.06d, 7.8d))
    index.add(item)
    index(1) should equal(item)
  }

  test("Retrieving non-existing item from hnsw index will throw") {
    val index = HnswIndexBuilder[Int, Double, IntDoubleIndexItem](
      dimensions = 3,
      maxItemCount = 1000000,
      m = 32,
      distanceCalculator = new EuclideanDistanceDouble
    ).build()

    intercept[NoSuchElementException] {
      index(1)
    }
  }

  test("Check if item is contained in Hnsw Index") {
    val index = HnswIndexBuilder[Int, Double, IntDoubleIndexItem](
      dimensions = 3,
      maxItemCount = 1000000,
      m = 32,
      distanceCalculator = new EuclideanDistanceDouble
    ).build()

    val item = IntDoubleIndexItem(1, Vector(4.05d, 1.06d, 7.8d))
    index.add(item)

    index.contains(item.id) should equal(true)
    index.contains(13131) should equal(false)
  }

  test("Find (all) nearest neighbours of item in Hnsw Index") {

    val index = HnswIndexBuilder[Int, Double, IntDoubleIndexItem](
      dimensions = 3,
      maxItemCount = 1000000,
      m = 32,
      distanceCalculator = new EuclideanDistanceDouble
    ).build()

    val item1 = IntDoubleIndexItem(1, Vector(4.05d, 1.06d, 7.8d))
    val item2 = IntDoubleIndexItem(2, Vector(8.01d, 2.06d, 1.8d))
    val item3 = IntDoubleIndexItem(3, Vector(9.34d, 3.06d, 3.1d))

    index.add(item1)
    index.add(item2)
    index.add(item3)

    val results = index.findKSimilarItems(item1.id, 10)

    results should contain theSameElementsInOrderAs List(
      SearchResult(item2.id, 7.258209145512411d),
      SearchResult(item3.id, 7.353509366282196d)
    )
  }

  test("Find nearest neighbours of item in a large Hnsw Index") {

    val index = HnswIndexBuilder[Int, Double, IntDoubleIndexItem](
      dimensions = 3,
      maxItemCount = 10000,
      m = 10,
      distanceCalculator = new EuclideanDistanceDouble
    ).build()

    (1 until 10000).foreach { i =>
      val item = IntDoubleIndexItem(i, Vector(4.05d + i, 1.06d, 7.8d))
      index.add(item)
    }

    val results = index.findKSimilarItems(1, 5).map(_.id)

    results should contain theSameElementsInOrderAs List(2, 3, 4, 5, 6)
  }
}
