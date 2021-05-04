package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.platform.transformer.sierra.source.SierraItemData

object SierraShelfmark {
  def apply(itemData: SierraItemData): Option[String] =
    // This is meant to be a "good enough" implementation of a shelfmark.
    // We may revisit this in future, and populate it directly from the
    // MARC fields if we want to be more picky about our rules.
    itemData.callNumber
}
