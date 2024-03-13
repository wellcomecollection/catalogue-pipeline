package weco.pipeline.transformer.marc_common.models

/*
 * Represents a MARC record,
 * This provides an interface for retrieving fields, subfields, and values
 * */
trait MarcRecord {

  val fields: Seq[MarcField]
  def fieldsWithTags(tags: String*): Seq[MarcField]

  def subfieldsWithTag(tag: (String, String)): List[MarcSubfield]
}
