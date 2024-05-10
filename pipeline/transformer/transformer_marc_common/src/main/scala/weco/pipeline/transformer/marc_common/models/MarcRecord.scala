package weco.pipeline.transformer.marc_common.models

/*
 * Represents a MARC record,
 * This provides an interface for retrieving fields, subfields, and values
 * */
trait MarcRecord {

  val leader: String

  val controlFields: Seq[MarcControlField]

  val fields: Seq[MarcField]

  // Only return a control field if there is exactly one with the given tag
  def controlField(tag: String): Option[MarcControlField] =
    controlFields.filter(_.marcTag == tag) match {
      case Nil      => None
      case x :: Nil => Some(x)
      case _        => None
    }

  def fieldsWithTags(tags: String*): Seq[MarcField]

  def subfieldsWithTag(tagPair: (String, String)): List[MarcSubfield]
}
