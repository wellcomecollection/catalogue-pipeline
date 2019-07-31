package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.platform.transformer.sierra.source.SierraBibData


trait SierraAlternativeTitle extends MarcUtils {

  // Populate work:alternativeTitle.
  //
  // We use the contents of MARC 246, unless we have ind 6 as that is used
  // for the lettering field

  def getAlternativeTitle(bibData: SierraBibData): Option[String] =
    getMatchingVarFields(bibData, "246")
      .filterNot { _.indicator2.contains("6") }
      .flatMap { getSubfieldContents(_, Some("a")) }
      .headOption
}
