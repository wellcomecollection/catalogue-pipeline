package weco.pipeline.transformer.sierra.transformers

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.work.CollectionPath
import weco.sierra.models.SierraQueryOps
import weco.sierra.models.data.SierraBibData
import weco.sierra.models.marc.VarField

import scala.util.matching.Regex

/**
 * Convert links between identified Sierra entries into a hierarchical path.
 *
 * The Sierra linking fields 773 and 774, when specified with the identifier subfield
 * "$w - Record control number" represent a part-whole relationship akin to those found
 * in CALM or TEI resources.
 *
 * - [773 - Host Item Entry](https://www.loc.gov/marc/bibliographic/bd773.html)
 * - [774 - Constituent Unit Entry](https://www.loc.gov/marc/bibliographic/bd774.html)
 *
 * When the identifier is not present, `773` fields represent membership of a Series, see
 * SierraParents.  Such fields are ignored here.
 *
 * At this point, the records are not 'Identified', moreover, the identifiers used in these
 * two fields are Worldcat ids (e.g. (Wcat)29062i), which do not get resolved by the id minter.
 *
 * Therefore, at this point we can only create the "link" via a common collection path,
 * to be later resolved by the relation embedder.
 *
 * A feature of these fields is that they often refer (via the $g - Related Parts subfield)
 * to a subsection of the whole. This subsection is not in itself an entity that exists and
 * can be identified or linked to.
 *
 * e.g. the following fields from two records, corresponding to both "ends" of the relationship
 *  - 773 0  |tBasil Hood. Photograph album,|gpage 5.|w(Wcat)9175i
 *  - 774 0  |gPage 5 :|tCharing Cross Hospital: a portrait of house surgeons. Photograph, 1906.|w(Wcat)28914i
 *    They each refer to the other, but there is no "page 5" object.
 *    So, although page 5 might look like it should be a node in the collectionPath, it cannot be.
 *
 * Notable challenges in the data include:
 *  - punctuation must be stripped ("Page 5 :" should match "page 5.")
 *  - case must be normalised ("Page 5" should match "page 5")
 *  - the (Wcat) prefix must be stripped ("(Wcat)9175i" should match "9175i")
 *  - the value must be turned into something the relation embedder expects
 *    (evidence points to this being underscores for spaces).
 *
 * The hierarchy of Sierra-based data is flatter than other systems. A node is expected to *either* be a host or a
 * constituent. It is possible for record that is a "host" to also be part of a series, but that relationship
 * would not contain the $w subfield, so is to be ignored here.
 *
 * A constituent has exactly one host
 * A host may have many constituents, but for the purpose of defining a CollectionPath, it only matters that one
 * exists, not how many there are.
 * */
object SierraCollectionPath extends SierraQueryOps with Logging {

  def apply(bibData: SierraBibData): Option[CollectionPath] = {
    if (bibData.subfieldsWithTags(("773", "w"), ("774", "w")).isEmpty)
      None
    else {
      (getOwnId(bibData), bibData.varfieldsWithTag("774")) match {
        case (Some(bibId), Nil) =>
          HostEntryFieldCollectionPath(bibData, bibId)
        case (Some(bibId), _) =>
          Some(CollectionPath(path = bibId, label = None))
      }
    }
  }

  private def getOwnId(bibData: SierraBibData): Option[String] = {
    bibData.varfieldsWithTag("001").headOption.flatMap(_.content)
  }
}


/**
 * Return an optional CollectionPath for a bib with host entry fields.
 *
 * The host entry field (773) has been used both for the identified
 * relationship with a reciprocal 774 on the other record, and for
 * unidentified Series membership.
 *
 * As such, a bib may have many host entry fields (773),
 * but at most, only one of them is expected to result in a CollectionPath.
 * This will be the one with a $w tag
 *
 * It is possible, but unlikely that a bib would have a mixture of both
 * The more common scenario is that it would have either exactly one 773
 * field, and that field has a $w subtag, or it would have multiple 773 fields,
 * and none of them have a $w subtag.
 */
object HostEntryFieldCollectionPath extends SierraQueryOps with Logging {
  val nonTokenCharacters = new Regex("[^0-9a-zA-Z_]")

  def apply(bibData: SierraBibData, bibId: String): Option[CollectionPath] = {
    // not bibData.subFieldsWithTag, because we later need to get the $g subtag
    // from *the same* varfield as the one with the $w subtag
    val hostEntryField: Option[VarField] =
    bibData.varfieldsWithTag("773").find(_.subfieldsWithTag("w").nonEmpty)

    if (hostEntryField.nonEmpty && bibId.nonEmpty)
      Some(CollectionPath(path = collectionPathString(hostEntryField.get, bibId), label = None))
    else
      None
  }

  /**
   * Return the path from the parent to this record as a / separated string.
   *
   * Being flat, the paths generated from 774/773 relationships consist of exactly two nodes.
   *
   * The $g - related part value is included, if present, as part of the node corresponding to
   * this record, this allows for alphanumeric sorting to put everything in (roughly) the correct order
   *
   * $g is not included as a node of its own, because it refers to something that does not exist
   * as an entity in its own right.
   *
   */
  private def collectionPathString(hostEntryField: VarField, ownId: String): String = {
    f"${getHostId(hostEntryField)}/${getRelatedPart(hostEntryField)}${ownId}"
  }

  /**
   * Extract the Related Part value from a host entry field, if any.
   * The Related Part ($g) subfield contains a free-text name of
   * the part of the host to which this record belongs.
   * This may be something like a page or volume number,
   * with or without text like "vol." or  "page: " etc.
   * The source data may contain punctuation, spaces etc,
   * all of which are removed or replaced
   * to make a token similar to those the Relation Embedder receives from
   * CALM and TEI
   */
  private def getRelatedPart(hostEntryField: VarField): String = {
    val gFields = hostEntryField.subfieldsWithTag("g")
    gFields match {
      case Nil => ""
      case _ => nonTokenCharacters.replaceAllIn(gFields.head.content.replaceAll(" ", "_"), "") + "_"
    }
  }

  private def getHostId(varField: VarField): String = {
    varField.subfieldsWithTag("w").head.content.stripPrefix("(Wcat)")
  }
}
