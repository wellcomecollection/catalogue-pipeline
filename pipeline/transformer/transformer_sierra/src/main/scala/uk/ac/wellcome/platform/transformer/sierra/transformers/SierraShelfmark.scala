package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.platform.transformer.sierra.source.{
  SierraItemData,
  SierraQueryOps
}

object SierraShelfmark extends SierraQueryOps {
  def apply(itemData: SierraItemData): Option[String] =
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
