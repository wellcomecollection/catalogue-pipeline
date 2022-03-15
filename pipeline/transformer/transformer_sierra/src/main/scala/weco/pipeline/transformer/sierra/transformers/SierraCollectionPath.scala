package weco.pipeline.transformer.sierra.transformers

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.work.CollectionPath
import weco.sierra.models.SierraQueryOps
import weco.sierra.models.data.SierraBibData
import weco.sierra.models.marc.{VarField}

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
 *  They each refer to the other, but there is no "page 5" object.
 *  So, although page 5 might look like it should be a node in the collectionPath, it cannot be.
 *
 *  Notable challenges in the data include:
 *  - punctuation must be stripped ("Page 5 :" should match "page 5.")
 *  - case must be normalised ("Page 5" should match "page 5")
 *  - the (Wcat) prefix must be stripped ("(Wcat)9175i" should match "9175i")
 *  - the value must be turned into something the relation embedder expects
 *      (evidence points to this being underscores for spaces).
 *
 *  The hierarchy of Sierra-based data is flatter than other systems. A node is expected to *either* be a host or a
 *  constituent. It is possible for record that is a "host" to also be part of a series, but that relationship
 *  would not contain the $w subfield, so is to be ignored here.
 *
 *  A constituent has exactly one host
 *  A host may have many constituents, but for the purpose of defining a CollectionPath, it only matters that one
 *  exists, not how many there are.
 *  */

object SierraCollectionPath extends SierraQueryOps with Logging {
  def apply(bibData: SierraBibData): Option[CollectionPath] = {
    bibData.varfieldsWithTag("774") match {
      case Nil => pathFromHostEntryField(bibData)
      case _ =>
        Some(CollectionPath(bibData.varfieldsWithTag("001").head.content.get))
      }
}
  private def pathFromHostEntryField(bibData: SierraBibData): Option[CollectionPath] = {
    val maybeIdentifiedHostEntryField: Option[VarField] = bibData.varfieldsWithTag("773").find(_.subfieldsWithTag("w").nonEmpty)
    if(maybeIdentifiedHostEntryField.isDefined) {
      val identifiedHostEntryField = maybeIdentifiedHostEntryField.get
      Some(CollectionPath(
        f"${identifiedHostEntryField.content}/${getRelatedPart(identifiedHostEntryField)}${bibData.varfieldsWithTag("001").head.content}"
      ))
    }
    None
  }

  private def getRelatedPart(hostEntryField: VarField): String = {
    val gFields = hostEntryField.subfieldsWithTag("g")
    gFields match {
      case Nil => ""
      case _ => gFields.head.content
    }
  }

}
