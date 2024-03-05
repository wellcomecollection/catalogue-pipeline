package weco.pipeline.transformer.marc_common.models

/*
 * Represents a MARC record,
 * This provides an interface for retrieving fields, subfields, and values
 * */
trait MarcRecord {
  // This is derived from the MARC21 Leader.
  // TODO: In the implementation(s), we will need to do something to harmonise the values from different sources
  //   because a given code from EBSCO may not meant the same thing as in Sierra.
  val materialTypeId: Option[String]

  val fields: Seq[MarcField]
  def fieldsWithTags(tags: String*): Seq[MarcField]
}
