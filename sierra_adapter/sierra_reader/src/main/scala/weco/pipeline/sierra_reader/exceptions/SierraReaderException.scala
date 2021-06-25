package weco.pipeline.sierra_reader.exceptions

case class SierraReaderException(e: Throwable) extends Exception(e.getMessage)

case object SierraReaderException {
  def apply(message: String): SierraReaderException =
    SierraReaderException(new RuntimeException(message))
}
