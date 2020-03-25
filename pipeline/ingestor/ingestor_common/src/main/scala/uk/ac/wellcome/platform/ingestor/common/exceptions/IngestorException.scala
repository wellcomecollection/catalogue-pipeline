package uk.ac.wellcome.platform.ingestor.common.exceptions

case class IngestorException(e: Throwable) extends Exception(e.getMessage)

case object IngestorException {
  def apply(message: String): IngestorException =
    IngestorException(new RuntimeException(message))
}
