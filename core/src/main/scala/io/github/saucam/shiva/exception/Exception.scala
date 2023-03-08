package io.github.saucam.shiva.exception

sealed abstract class IndexException(msg: String) extends Exception(msg)

case class SizeLimitExceededException(msg: String) extends IndexException(msg)
