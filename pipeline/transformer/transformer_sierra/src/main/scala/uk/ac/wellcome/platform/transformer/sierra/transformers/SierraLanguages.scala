package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.models.work.internal.Language
import uk.ac.wellcome.platform.transformer.sierra.source.SierraBibData

object SierraLanguages extends SierraDataTransformer {
  type Output = Option[Seq[Language]]

  // Populate wwork:language.
  //
  // Rules:
  //
  //  - The first language comes from 008/35-37.  The Sierra API provides
  //    this via the "lang" field.
  //    See https://www.loc.gov/marc/bibliographic/bd008.html
  //
  //  - Subsequent languages come from MARC 041 ǂa.
  //    This is a repeatable field, as is the subfield.
  //    See https://www.loc.gov/marc/bibliographic/bd041.html
  //
  override def apply(bibData: SierraBibData): Option[Seq[Language]] =
    None
}
