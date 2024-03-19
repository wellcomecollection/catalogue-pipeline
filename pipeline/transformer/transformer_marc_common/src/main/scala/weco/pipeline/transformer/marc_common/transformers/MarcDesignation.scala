package weco.pipeline.transformer.marc_common.transformers

import weco.pipeline.transformer.marc_common.models.{
  MarcField,
  MarcRecord,
  MarcSubfield
}
import weco.pipeline.transformer.exceptions.ShouldNotTransformException
import weco.pipeline.transformer.marc_common.logging.LoggingContext

object MarcDesignation extends MarcDataTransformerWithLoggingContext {

  type Output = Seq[String]

  def apply(record: MarcRecord)(implicit ctx: LoggingContext): Seq[String] = {
    record.fieldsWithTags("362").flatMap(field => singleDesignation(field))
  }
  private def singleDesignation(
    field: MarcField
  )(implicit ctx: LoggingContext): Option[String] =
    field.subfields.filter(_.tag == "a") match {
      case Seq(MarcSubfield("a", content)) => Some(content.trim)
      case Nil                             => None
      case _                               =>
        // At time of writing, there were no instances of 362 with multiple ǂa
        // subfields in Sierra or the EBSCO sample data.
        // Therefore, it should be safe to reject any records that do, and
        // alert someone in control of the source data.
        throw new ShouldNotTransformException(
          ctx(s"multiple non-repeating fields found - 362ǂa - $field")
        )
    }

}
