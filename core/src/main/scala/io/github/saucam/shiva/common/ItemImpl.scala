package io.github.saucam.shiva.common

import breeze.linalg._

case class IntDoubleIndexItem(id: Int, vector: Vector[Double]) extends Item[Int, Double] {
  override def dimension(): Int = vector.length
}

case class LongDoubleIndexItem(id: Long, vector: Vector[Double]) extends Item[Long, Double] {
  override def dimension(): Int = vector.length
}

case class StringDoubleIndexItem(id: String, vector: Vector[Double]) extends Item[String, Double] {
  override def dimension(): Int = vector.length
}
