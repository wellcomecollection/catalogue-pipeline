package weco.pipeline.transformer.sierra.transformers

import weco.sierra.models.SierraQueryOps
import weco.sierra.models.data.{SierraBibData, SierraItemData}
import weco.sierra.models.fields.SierraMaterialType

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
      // on the item -- if they have a common prefix, we copy across the shelfmark,
      // but otherwise we discard it.
      //
      // e.g. 12345i and 12345i.1
      //
      // If several pictures have been mounted on the same frame, they get a single
      // item (because you request them all together).  Each picture would get its
      // own i-number and its own bib, and the item record would record all the item
      // numbers on the frame.
      //
      // e.g. 12345i, 12346i, 12347i
      //
      case _
          if bibData.hasIconographicNumber && itemData.shelfmarkStartsWith(
            bibData.iconographicNumber + ".") =>
        itemData.shelfmark

      case _ if bibData.hasIconographicNumber =>
        None

      case _ => itemData.shelfmark
    }

  private implicit class ItemShelfmarkOps(itemData: SierraItemData) {
    def shelfmark: Option[String] =
      // We use the contents of field 949 subfield ǂa for now.  We expect we'll
      // gradually be pickier about whether we use this value, or whether we
      // suppress it.
      //
      // We used to use the callNumber field on Sierra item records, but this
      // draws from some MARC fields that we don't want (including 999).
      //
      // Field tag c is for "call number" data.  In particular, we don't want
      // to expose field tag a, which has legacy data we don't want to display.
      // See https://wellcome.slack.com/archives/CGXDT2GSH/p1622719935015500?thread_ts=1622719185.015400&cid=CGXDT2GSH
      itemData.varFields
        .filter { _.marcTag.contains("949") }
        .filter { _.fieldTag.contains("c") }
        .subfieldsWithTags("a")
        .headOption
        .map { _.content.trim }

    def shelfmarkStartsWith(prefix: String): Boolean =
      shelfmark match {
        case Some(s) if s.startsWith(prefix) && s != prefix => true
        case _                                              => false
      }
  }

  private implicit class BibShelfmarkOps(bibData: SierraBibData) {
    def isArchivesAndManuscripts: Boolean =
      bibData.materialType.contains(SierraMaterialType("h"))

    def hasIconographicNumber: Boolean =
      SierraIconographicNumber(bibData).isDefined

    def iconographicNumber: String =
      SierraIconographicNumber(bibData).get
  }
}
