package weco.pipeline.transformer.marc_common.transformers

import weco.pipeline.transformer.marc_common.models.MarcRecord

object MarcEdition extends MarcDataTransformer {

  type Output = Option[String]

  def apply(record: MarcRecord): Option[String] =
    Option(
      record
        .subfieldsWithTag("250" -> "a")
        .map(_.content.trim)
        .mkString(" ")
    ).filter(_.trim.nonEmpty)

}
