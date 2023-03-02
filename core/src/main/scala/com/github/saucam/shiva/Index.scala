package com.github.saucam.shiva

trait Index[I] extends Serializable {

  /**
   * Add a new vector to the index. Returns the id of the newly added vector in the index,
   * which can later be used to retrieve the vector. Returns -1 if a problem is encountered
   * while adding the vector.
   * @param v
   */
  def add(v: I): Int

  /**
   * Check if item is present in the index
   * @param id unique identifier of the item
   * @return
   */
  def contains(id: Int): Boolean

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
   * Returns a Vector by its identifier
   * @param id
   * @return
   */
  def get(id: Int): Option[I]
}
