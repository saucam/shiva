package io.github.saucam.shiva

import breeze.linalg._
import io.github.saucam.shiva.common.Item

/**
 * Result of a search
 * @param id: id of the item
 * @param distance: distance of the item
 * @tparam TId: type of the id of the item
 * @tparam V: type of the distance
 */
case class SearchResult[TId, V](id: TId, distance: V)

/**
 * Trait that defines the index of items.
 * @tparam TId: type of the id of the item to be added to the index
 * @tparam I: type of item to be added to the index
 */
trait ItemIndex[TId, V, I <: Item[TId, V]] extends Serializable {

  /**
   * Returns item by its identifier. NoSuchElementException is thrown if item
   * does not exist in the index.
   * @param id: unique id of the item.
   * @return item
   */
  def apply(id: TId): I = get(id).getOrElse(throw new NoSuchElementException)

  /**
   * Add a new item to the index. Returns true if item was success
   * @param v: item to be added to the index.
   */
  def add(v: I): Boolean

  /**
   * Check if item is present in the index
   * @param id unique id of the item
   * @return true if item is present in the index, false otherwise
   */
  def contains(id: TId): Boolean

  /**
   * Add all vectors to the index. Returns the ids of the newly added vectors in the index,
   * in the same order that the vectors were supplied.
   * @param iterV collection of vectors to be added
   * @return collection of ids for each of the vector in iterV. id will be -1 if a problem
   * was encountered while adding the corresponding vector.
   */
  def addAll(iterV: Iterable[I]): Iterable[Int]

  /**
   * @return size of the index
   */
  def size(): Int

  /**
   * Returns an Item by its identifier
   * @param id: unique id of the item.
   * @return item if it exists in the index, None otherwise.
   */
  def get(id: TId): Option[I]

  /**
   * Returns the nearest neighbors of a vector in the index.
   * @param vector: vector to find the nearest neighbors for in the index.
   * @param k: number of nearest neighbors to return.
   * @return list of nearest neighbors.
   */
  def findNearestNeighbors(vector: Vector[V], k: Int): List[SearchResult[TId, V]]

  /**
   * Returns the k most similar items to the item with the given id.
   * @param id: unique id of the item.
   * @param k: number of similar items to return.
   * @return list of k most similar items.
   */
  def findKSimilarItems(id: TId, k: Int): List[SearchResult[TId, V]] =
    get(id)
      .map(item => findNearestNeighbors(item.vector, k + 1).filter(_.id != id))
      .getOrElse(List.empty)
}
