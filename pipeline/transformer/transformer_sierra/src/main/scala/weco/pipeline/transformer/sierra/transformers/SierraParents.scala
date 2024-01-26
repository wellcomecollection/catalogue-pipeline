package weco.pipeline.transformer.sierra.transformers

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.work.{Relation, SeriesRelation}
import weco.sierra.models.SierraQueryOps
import weco.sierra.models.data.SierraBibData
import weco.sierra.models.marc.VarField

/*
 * Implementation of Parent links in Sierra records.
 *
 * Conceptually, A work may link either to another Work, or to a "Series",
 * which is not an entity in its own right, but simply a title under which
 * multiple Works may be listed.
 *
 * https://github.com/wellcomecollection/docs/tree/main/rfcs/045-sierra-work-relationships
 *
 * At present, all parent links behave as though they link to a Series.
 * */

object SierraParents extends SierraQueryOps with Logging {

  /** Convert the parent link MARC fields into Relation objects.
    *
    * Links to a parent are found in four different MARC fields These are all to result in a Series
    * Relation with the title taken from the MARC field.
    *
    *   - [440 - Series Statement/Added
    *     Entry-Title](https://www.loc.gov/marc/bibliographic/bd440.html)
    *   - [490 - Series Statement](https://www.loc.gov/marc/bibliographic/bd490.html)
    *   - [773 - Host Item Entry](https://www.loc.gov/marc/bibliographic/bd773.html)
    *   - [830 - Series Added Entry-Uniform Title](https://www.loc.gov/marc/bibliographic/bd830.html
    *
    * All Series Relations resulting from this:
    *   - Have a non-empty title
    *   - Are stripped of subfield separators (see below)
    *   - Are unique
    *
    * MARC fields are designed to be output by concatenating the whole field (i.e. the content and
    * subfields) in document order. As such, they may contain punctuation (and spacing) intended to
    * presented in that context specifically. In this usage, we are separating the main content from
    * the subfield so this punctuation is not wanted.
    */
  def apply(bibData: SierraBibData): List[Relation] = {
    bibData
      .varfieldsWithTags("440", "490", "773", "830")
      .flatMap(titleFromVarField)
      .map(title => title.stripSuffix(";").stripSuffix(",").trim)
      .filter(_.nonEmpty)
      .distinct
      .map(
        SeriesRelation(_)
      )
  }

  /** Return the title of the parent object represented by the given VarField The part of the field
    * that represents the title varies by which MARC tag is in use. 773 fields normally have no main
    * field content, the title is in one of the 'title' subfields 440, 490 and 830 fields normally
    * keep it in the main field content. There are three subfields that may represent a title - $a,
    * $s, $t
    *   - any of these fields may use the $a subfield.
    *   - 4XX fields only have the $a subfield
    *   - the $s subfield in an 830 field has a different meaning.
    *
    * In practice, there should be no problem with looking for $t and $a on any field, because $a is
    * always possible and $t means the same on both 773 and 830, and simply won't be there on 4XX.
    * However, 830 $s means Version, so must not be included in the lookup for 830 fields.
    */
  private val subFieldTags = Map[String, List[String]](
    "440" -> List("a"),
    "490" -> List("a"),
    "773" -> List("t", "a", "s"),
    "830" -> List("t", "a")
  )

  private def titleFromVarField(field: VarField): Option[String] = {
    val marcTag = field.marcTag.get
    val subfieldTagsForField = subFieldTags(marcTag)
    field.subfieldsWithTags(subfieldTagsForField: _*) match {
      case Nil =>
        if (!field.content.exists(_.nonEmpty)) {
          warn(
            s"A $marcTag field is expected to have a title in the field content or one of the title subfields (${subfieldTagsForField
                .mkString(", ")}), there was none: $field"
          )
        }
        field.content
      case subfields =>
        if (subfields.tail.nonEmpty || field.content.exists(_.nonEmpty)) {
          warn(
            s"Ambiguous $marcTag Series relationship, only one of ${subfieldTagsForField
                .mkString(", ")} or the field content is expected to be populated $field"
          )
        }
        Some(subfields.head.content)
    }
  }
}
