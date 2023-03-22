package io.github.saucam.shiva.common

import it.unimi.dsi.fastutil.ints.IntArrayList

case class Node[TId, V, I <: Item[TId, V]](id: Int, item: I, connections: Array[IntArrayList]) extends Serializable {

  def maxLevel(): Int = connections.length - 1
}
