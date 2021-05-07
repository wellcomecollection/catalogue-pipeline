package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.platform.transformer.sierra.source.{
  SierraBibData,
  SierraItemData,
  SierraMaterialType,
  SierraQueryOps
}
import weco.catalogue.internal_model.work.Format.ArchivesAndManuscripts

object SierraShelfmark extends SierraQueryOps {
  def apply(bibData: SierraBibData, itemData: SierraItemData): Option[String] =
    bibData.materialType match {
      // The shelfmarks for Archives & Manuscripts are a duplicate of the
      // reference number and the box number, the latter of which we don't
      // want to expose publicly.
      //
      // e.g. PP/ABC/D.2/3:Box 5
      //
      case Some(SierraMaterialType(ArchivesAndManuscripts.id)) =>
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
}
