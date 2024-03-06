package weco.pipeline.transformer.marc_common.generators

import org.scalatest.LoneElement
import weco.pipeline.transformer.marc_common.models.{MarcField, MarcRecord}

case class MarcTestRecord(
  fields: Seq[MarcField]
) extends MarcRecord
    with LoneElement {
  def fieldsWithTags(tags: String*): Seq[MarcField] =
    fields.filter(field => tags.contains(field.marcTag))

  def nonRepeatableFieldWithTag(tag: String): Option[MarcField] =
    Some(fieldsWithTags(tag).loneElement)

}
