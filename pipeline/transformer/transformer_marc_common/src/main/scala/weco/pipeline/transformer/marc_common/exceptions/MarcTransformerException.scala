package weco.pipeline.transformer.marc_common.exceptions

case class MarcTransformerException(e: Throwable)
    extends Exception(e.getMessage)

case object MarcTransformerException {
  def apply(message: String): MarcTransformerException =
    MarcTransformerException(new RuntimeException(message))
}
