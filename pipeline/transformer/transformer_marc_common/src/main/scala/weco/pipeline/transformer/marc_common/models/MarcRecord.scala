package weco.pipeline.transformer.marc_common.models

/*
 * Represents a MARC record,
 * This provides an interface for retrieving fields, subfields, and values
 * */
trait MarcRecord {

  val leader: String

  val controlFields: Seq[MarcControlField]

  val fields: Seq[MarcField]

  def controlField(tag: String): Option[MarcControlField] =
    controlFields.find(_.marcTag == tag)

  def fieldsWithTags(tags: String*): Seq[MarcField]

  def subfieldsWithTag(tagPair: (String, String)): List[MarcSubfield]
}
