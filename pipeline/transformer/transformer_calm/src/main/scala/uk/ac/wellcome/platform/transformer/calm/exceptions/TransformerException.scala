package uk.ac.wellcome.platform.transformer.calm.exceptions

sealed trait TransformerException

object TransformerException {
  final case object SourceDataSerialiseException extends TransformerException
  final case object SourceDataTransformerException extends TransformerException
  final case object FieldTransformerException extends TransformerException
  final case object FieldMissingTransformerException
      extends TransformerException
}
