package weco.pipeline.transformer.marc_common.transformers

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.work.CollectionPath
import weco.pipeline.transformer.marc_common.models.{
  MarcField,
  MarcFieldOps,
  MarcRecord
}
import scala.util.matching.Regex

/** Convert links between records into a hierarchical path.
  *
  * The Marc fields 773 and 774, when specified with the identifier subfield "$w
  * \- Record control number" represent a part-whole relationship akin to those
  * found in CALM or TEI resources.
  *
  *   - [773 - Host Item
  *     Entry](https://www.loc.gov/marc/bibliographic/bd773.html)
  *   - [774 - Constituent Unit
  *     Entry](https://www.loc.gov/marc/bibliographic/bd774.html)
  *
  * When the identifier is not present, `773` fields represent membership of a
  * Series, see SierraParents. Such fields are ignored here.
  *
  * At this point, the records are not 'Identified', moreover, the identifiers
  * used in these two fields are Worldcat ids (e.g. (Wcat)29062i), which do not
  * get resolved by the id minter.
  *
  * Therefore, at this point we can only create the "link" via a common
  * collection path, to be later resolved by the relation embedder.
  *
  * A feature of these fields is that they often refer (via the $g - Related
  * Parts subfield) to a subsection of the whole. This subsection is not in
  * itself an entity that exists and can be identified or linked to.
  *
  * e.g. the following fields from two records, corresponding to both "ends" of
  * the relationship
  *   - 773 0 |tBasil Hood. Photograph album,|gpage 5.|w(Wcat)9175i
  *   - 774 0 |gPage 5 :|tCharing Cross Hospital: a portrait of house surgeons.
  *     Photograph, 1906.|w(Wcat)28914i They each refer to the other, but there
  *     is no "page 5" object. So, although page 5 might look like it should be
  *     a node in the collectionPath, it cannot be.
  *
  * Notable challenges in the data include:
  *   - punctuation must be stripped ("Page 5 :" should match "page 5.")
  *   - the (Wcat) prefix must be stripped ("(Wcat)9175i" should match "9175i")
  *   - the value must be turned into something the relation embedder expects
  *     (evidence points to this being underscores for spaces).
  *
  * The hierarchy of Sierra-based data is flatter than other systems. A node is
  * normally only a host or a constituent, but three-level hierarchies are
  * possible. It is possible for record that is a "host" to also be part of a
  * series, but that relationship would not contain the $w subfield, so is to be
  * ignored here. Such series relationships are handled in SierraParents
  *
  * A constituent has exactly one host. It is possible to have multiple 773
  * entries, but, as with a host being part of a series, entries without $w are
  * handled in SierraParents and signify membership of a Series, not a
  * hierarchical collection.
  *
  * A host may have many constituents, but for the purpose of defining a
  * CollectionPath, it only matters that one exists, not how many there are.
  */

object MarcCollectionPath extends MarcDataTransformer with Logging {
  override type Output = Option[CollectionPath]

  override def apply(record: MarcRecord): Option[CollectionPath] = {
    if (
      (record.subfieldsWithTag(("773", "w")) ++ record.subfieldsWithTag(
        ("774", "w")
      )).isEmpty
    )
      None
    else {
      (record.controlField("001"), record.fieldsWithTags("774")) match {
        case (None, _) =>
          // Without an identifier for the current document, we cannot hope
          // to construct a path to it
          warn(
            f"Attempt to create CollectionPath for record without a control number field ${record}"
          )
          None
        case (Some(controlField001), constituentUnits) =>
          // Optionally construct a `parent/this`
          // collectionPath from an appropriate 773 host entry field if available.
          val maybeHostPath =
            HostEntryFieldCollectionPath(record, controlField001.content)

          (maybeHostPath, constituentUnits) match {
            // This document does not contain any constituent unit entries,
            // It is therefore probably a leaf in the hierarchy.
            // So return the 'parent/this' path
            case (_, Nil) => maybeHostPath
            // This document does not have a host entry,
            // but does contain constituent units.
            // It is therefore probably the root of a hierarchy
            // So return 'this' as a path
            case (None, _) =>
              Some(CollectionPath(path = controlField001.content, label = None))
            // This document has both a host and constituent units,
            // It is therefore probably a branch in the hierarchy
            // again, return the 'parent/this' path
            case (Some(hostPath), _) =>
              Some(CollectionPath(path = hostPath.path, label = None))
          }
      }
    }
  }

  /** Return an optional CollectionPath for a bib with host entry fields.
    *
    * The host entry field (773) has been used both for the identified
    * relationship with a reciprocal 774 on the other record, and for
    * unidentified Series membership.
    *
    * As such, a record may have many host entry fields (773), but at most, only
    * one of them is expected to result in a CollectionPath. This will be the
    * one with a $w tag
    *
    * It is possible, but unlikely, that a record would have a mixture of both.
    * The more common scenario is that it would have either exactly one 773
    * field, and that field has a $w subtag, or it would have multiple 773
    * fields, and none of them have a $w subtag.
    */
  private object HostEntryFieldCollectionPath
      extends MarcFieldOps
      with Logging {
    val nonTokenCharacters = new Regex("[^0-9a-zA-Z_]")

    def apply(record: MarcRecord, recordId: String): Option[CollectionPath] = {
      // not bibData.subFieldsWithTag, because we later need to get the $g subtag
      // from *the same* MarcField as the one with the $w subtag
      val hostEntryField: Option[MarcField] = {
        record.fieldsWithTags("773").find {
          case MarcField(_, subfields, _, _, _) =>
            subfields.exists(_.tag == "w")
        }
      }

      if (hostEntryField.isDefined && recordId.nonEmpty) {
        collectionPathString(hostEntryField.get, recordId.trim) map {
          path =>
            CollectionPath(
              path = path,
              label = None
            )
        }
      } else {
        // Should not be possible to reach this point, MarcCollectionPath.apply will have
        // ensured that an appropriate 773 entry exists somewhere in the document.
        warn(
          f"Could not find a MarcField suitable for making a collectionPath ${record}"
        )
        None
      }
    }

    /** Return the path from the parent to this record as a / separated string.
      *
      * Being flat, the paths generated from 774/773 relationships consist of
      * exactly two nodes.
      *
      * The $g - related part value is included, if present, as part of the node
      * corresponding to this record, this allows for alphanumeric sorting to
      * put everything in (roughly) the correct order
      *
      * $g is not included as a node of its own, because it refers to something
      * that does not exist as an entity in its own right.
      */
    private def collectionPathString(
      hostEntryField: MarcField,
      ownId: String
    ): Option[String] = {
      val hostId = getHostId(hostEntryField)
      if (hostId == ownId) {
        warn(
          s"self referential host ID - $hostEntryField refers to this document's own control number: $ownId"
        )
        None
      } else
        Some(f"$hostId/${getRelatedPart(hostEntryField)}$ownId")
    }

    /** Extract the Related Part value from a host entry field, if any. The
      * Related Part ($g) subfield contains a free-text name of the part of the
      * host to which this record belongs. This may be something like a page or
      * volume number, with or without text like "vol." or "page: " etc. The
      * source data may contain punctuation, spaces etc, all of which are
      * removed or replaced to make a token similar to those the Relation
      * Embedder receives from CALM and TEI
      */
    private def getRelatedPart(hostEntryField: MarcField): String = {
      val gFields = hostEntryField.subfieldsWithTag("g")

      gFields match {
        case Nil => ""
        case _ =>
          nonTokenCharacters.replaceAllIn(
            gFields.head.content.replace(' ', '_'),
            ""
          ) + "_"
      }
    }

    /** This logic is specific to marc records from Sierra, but it's fairly
      * harmless to have it here. In addition if Sierra records are ported to
      * other systems we will very likely have to deal with this data in the
      * same way.
      */
    private def getHostId(hostEntryField: MarcField): String = {
      hostEntryField
        .subfieldsWithTag("w")
        .head
        .content
        .stripPrefix("(Wcat)")
        .trim
    }
  }
}
