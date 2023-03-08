package io.github.saucam.shiva

/**
 * @tparam TId type of the id of the item to be added to the index
 * @tparam I type of item to be added to the index
 */
trait Index[TId, I] extends Serializable {

  /**
   * Add a new item to the index. Returns true if item was success
   * @param v
   */
  def add(v: I): Boolean

  /**
   * Check if item is present in the index
   * @param id unique id of the item
   * @return
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
   * @param id
   * @return
   */
  def get(id: TId): Option[I]
}
