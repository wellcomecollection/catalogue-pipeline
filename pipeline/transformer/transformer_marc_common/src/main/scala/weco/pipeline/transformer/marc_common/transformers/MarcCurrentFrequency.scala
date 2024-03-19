package weco.pipeline.transformer.marc_common.transformers
import weco.pipeline.transformer.marc_common.models.{MarcRecord, MarcSubfield}

object MarcCurrentFrequency extends MarcDataTransformer {

  override type Output = Option[String]

  override def apply(record: MarcRecord): Option[String] = {
    Option(
      record
        .fieldsWithTags("310")
        .map(
          field =>
            field.subfields
              .collect {
                case MarcSubfield(tag, content)
                    if Seq("a", "b").contains(tag) =>
                  content
              }
              .mkString(" ")
        )
        .map(_.trim)
        .mkString(" ")
    ).filter(_.trim.nonEmpty)
  }
}
