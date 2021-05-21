package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.platform.transformer.sierra.source.SierraQueryOps
import weco.catalogue.source_model.sierra.source.SierraMaterialType
import weco.catalogue.source_model.sierra.{SierraBibData, SierraItemData}

object SierraShelfmark extends SierraQueryOps {
  def apply(bibData: SierraBibData, itemData: SierraItemData): Option[String] =
    bibData match {
      // The shelfmarks for Archives & Manuscripts are a duplicate of the
      // reference number and the box number, the latter of which we don't
      // want to expose publicly.
      //
      // e.g. PP/ABC/D.2/3:Box 5
      //
      case _ if bibData.isArchivesAndManuscripts =>
        None

      // In the Iconographic Collection, 949 ǂa may contain i-numbers.  These are
      // required for LE&E staff to find the item in our stores, but they aren't
      // useful to show to users, so we hide them.
      //
      // e.g. 12345i
      //
      // Note that the iconographic number in 001 on the bib may not match 949 ǂa
      // on the item -- that's fine and expected.
      //
      // If several pictures have been mounted on the same frame, they get a single
      // item (because you request them all together).  Each picture would get its
      // own i-number and its own bib, and the item record would record all the item
      // numbers on the frame.
      //
      // e.g. 12345i, 12346i, 12347i
      //
      case _ if bibData.hasIconographicNumber =>
        None

      case _ =>
        // We use the contents of field 949 subfield ǂa for now.  We expect we'll
        // gradually be pickier about whether we use this value, or whether we
        // suppress it.
        //
        // We used to use the callNumber field on Sierra item records, but this
        // draws from some MARC fields that we don't want (including 999).
        itemData.varFields
          .filter { vf =>
            vf.marcTag.contains("949")
          }
          .subfieldsWithTags("a")
          .headOption
          .map { _.content.trim }
    }

  private implicit class BibShelfmarkOps(bibData: SierraBibData) {
    def isArchivesAndManuscripts: Boolean =
      bibData.materialType.contains(SierraMaterialType("h"))

    def hasIconographicNumber: Boolean =
      SierraIconographicNumber(bibData).isDefined
  }
}
