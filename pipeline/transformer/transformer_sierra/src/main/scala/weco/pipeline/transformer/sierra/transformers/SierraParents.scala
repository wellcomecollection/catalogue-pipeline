package weco.pipeline.transformer.sierra.transformers

import weco.catalogue.internal_model.work.Relation
import weco.sierra.models.SierraQueryOps
import weco.sierra.models.data.SierraBibData

import scala.util.matching.Regex

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

object SierraParents extends SierraQueryOps {

  // MARC fields are designed to be output by concatenating the whole field
  // (i.e. the content and subfields) in document order.
  // As such, they may contain punctuation (and spacing)
  // intended to presented in that context specifically.
  // In this usage, we are separating the main content from the subfield
  // so this punctuation is not wanted.
  // Expand this regex as more separators are discovered
  val TailSeparator: Regex = "[;]\\s?$".r

  /**
    *  Convert the parent link MARC fields into Relation objects.
    *
    *  Links to a parent are found in four different MARC fields
    *  These are all to result in a Series Relation with the title
    *  taken from the MARC field.
    *
    *  All Series Relations resulting from this:
    *  - Have a non-empty title
    *  - Are stripped of subfield separators
    *  - Are unique
    */
  def apply(bibData: SierraBibData): List[Relation] =
    bibData
      .varfieldsWithTags("440", "490", "773", "830")
      .flatMap(_.content)
      .map(title => TailSeparator.replaceFirstIn(title, "").trim())
      .filter(_.nonEmpty)
      .distinct
      .map(
        Relation(_)
      )
}
