package io.github.saucam.shiva.common

import breeze.linalg._
import org.scalatest.Inspectors
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class DistanceCalculatorImplSpec extends AnyFunSuite with Matchers with Inspectors {

  val kf: Vector[Float] = Vector.apply(0.01f, 0.02f, 0.03f)
  val rf: Vector[Float] = Vector.apply(0.03f, 0.02f, 0.01f)

  val kd: Vector[Double] = Vector.apply(0.01d, 0.02d, 0.03d)
  val rd: Vector[Double] = Vector.apply(0.03d, 0.02d, 0.01d)

  val marginf: Float = 1e-4f
  val margind: Double = 1e-4d

  test("Inner product float distance is calculated correctly") {

    val ip = new InnerProductFloat()

    ip.computeDistance(kf, rf) should be(0.999f +- marginf)
  }

  test("Inner product double distance is calculated correctly") {

    val ip = new InnerProductDouble()

    ip.computeDistance(kd, rd) should be(0.999d +- margind)
  }

  test("Euclidean distance float is calculated correctly") {
    val edf = new EuclideanDistanceFloat

    edf.computeDistance(kf, rf) should be(0.02828427f +- marginf)
  }

  test("Euclidean distance double is calculated correctly") {
    val edf = new EuclideanDistanceDouble

    edf.computeDistance(kd, rd) should be(0.02828427d +- margind)
  }

  test("Cosine similarity distance float is calculated correctly") {
    val csf = new CosineSimilarityFloat

    csf.computeDistance(kf, rf) should be(0.28571428f +- marginf)
  }

  test("Cosine similarity distance double is calculated correctly") {
    val csd = new CosineSimilarityDouble

    csd.computeDistance(kd, rd) should be(0.28571428d +- margind)
  }

  test("Manhattan distance float is calculated correctly") {
    val mdf = new ManhattanDistanceFloat

    mdf.computeDistance(kf, rf) should be(0.04f +- marginf)
  }

  test("Manhattan distance double is calculated correctly") {
    val mdd = new ManhattanDistanceDouble

    mdd.computeDistance(kd, rd) should be(0.04d +- margind)
  }
  test("Minkowski distance float is calculated correctly") {
    // Minkowski distance with p = 2 is the same as Euclidean distance
    val mdf = new MinkowskiDistanceFloat(2)

    val edf = new EuclideanDistanceFloat
    val ed = edf.computeDistance(kf, rf)

    mdf.computeDistance(kf, rf) should be(ed +- marginf)
  }

  test("Minkowski distance double is calculated correctly") {
    val mdd = new MinkowskiDistanceDouble(2)

    val edf = new EuclideanDistanceDouble
    val ed = edf.computeDistance(kd, rd)

    mdd.computeDistance(kd, rd) should be(ed +- margind)
  }
}
