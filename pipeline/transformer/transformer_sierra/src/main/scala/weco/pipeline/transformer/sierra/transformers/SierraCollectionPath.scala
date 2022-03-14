package weco.pipeline.transformer.sierra.transformers

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.work.CollectionPath
import weco.sierra.models.SierraQueryOps
import weco.sierra.models.data.SierraBibData

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
  * SierraParents.
  *
  * At this point, the records are not 'Identified', moreover, the identifiers used in these
  * two fields are Worldcat ids (e.g. (Wcat)29062i), which do not get resolved by the id minter.
  *
  * Therefore, at this point we can only create the "link" via a collection path, to be later
  * resolved by the relation embedder.
  *
  * A feature of these fields is that they often refer (via the $g - Related Parts subfield)
  * to a subsection of the whole. This subsection is not in itself an entity that exists and
  * can be identified or linked to.
  *
  * e.g. the following fields from two records, corresponding to both "ends" of the relationship
  *  - 774 0  |gPage 5 :|tCharing Cross Hospital: a portrait of house surgeons. Photograph, 1906.|w(Wcat)28914i
  *  - 773 0  |tBasil Hood. Photograph album,|gpage 5.|w(Wcat)9175i
  *  They each refer to the other, but there is no "page 5" object.
  *  So, although page 5 might look like it should be a node in the collectionPath, it cannot be.,
  *  */
object SierraCollectionPath extends SierraQueryOps with Logging {
  def apply(bibData: SierraBibData): Option[CollectionPath] = {
    bibData.varfieldsWithTags("773", "774")
    Some(CollectionPath("banana"))
    None
  }

}
