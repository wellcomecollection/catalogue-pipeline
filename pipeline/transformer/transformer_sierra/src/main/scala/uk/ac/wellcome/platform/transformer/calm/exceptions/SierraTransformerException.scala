package uk.ac.wellcome.platform.transformer.calm.exceptions

case class SierraTransformerException(e: Throwable)
    extends Exception(e.getMessage)

case object SierraTransformerException {
  def apply(message: String): SierraTransformerException =
    SierraTransformerException(new RuntimeException(message))
}
