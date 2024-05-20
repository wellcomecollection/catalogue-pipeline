package weco.pipeline.transformer.marc_common.generators

import org.scalatest.LoneElement
import weco.pipeline.transformer.marc_common.models.{
  MarcControlField,
  MarcField,
  MarcRecord,
  MarcSubfield
}

case class MarcTestRecord(
  fields: Seq[MarcField] = Nil,
  controlFields: Seq[MarcControlField] = Nil
) extends MarcRecord
    with LoneElement {
  def fieldsWithTags(tags: String*): Seq[MarcField] =
    fields.filter(field => tags.contains(field.marcTag))

  def nonRepeatableFieldWithTag(tag: String): Option[MarcField] =
    Some(fieldsWithTags(tag).loneElement)

  override def subfieldsWithTag(tagPair: (String, String)): List[MarcSubfield] =
    tagPair match {
      case (tag, subfieldTag) =>
        fieldsWithTags(tag)
          .flatMap(_.subfields)
          .filter(_.tag == subfieldTag)
          .toList
    }

  // These are not used in the tests, but we need to implement them to satisfy the interface
  override val leader: String = ""
}
