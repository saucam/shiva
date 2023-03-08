package io.github.saucam.shiva.common

import breeze.linalg._
import org.scalatest.Inspectors
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class DistanceCalculatorSpec extends AnyFunSuite with Matchers with Inspectors {

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

    edf.computeDistance(kd, rd) should be(0.02828427d +- marginf)
  }
}
