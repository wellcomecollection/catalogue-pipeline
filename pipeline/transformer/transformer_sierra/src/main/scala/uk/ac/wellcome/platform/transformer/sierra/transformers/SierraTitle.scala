package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.platform.transformer.sierra.exceptions.ShouldNotTransformException
import uk.ac.wellcome.platform.transformer.sierra.source.{
  SierraBibData,
  SierraQueryOps
}

object SierraTitle extends SierraDataTransformer with SierraQueryOps {

  type Output = Option[String]

  // Populate wwork:title.  The rules are as follows:
  //
  //    Join MARC field 245 subfields ǂa, ǂb, ǂc, ǂn and ǂp with a space.
  //    They should be joined in the same order as the original subfields.
  //
  // MARC 245 is non-repeatable, as are subfields ǂa, ǂb and ǂc.  However,
  // there are records in Wellcome's catalogue that repeat them, so we deviate
  // from the MARC spec here.
  //
  // http://www.loc.gov/marc/bibliographic/bd245.html
  def apply(bibData: SierraBibData): Option[String] = {
    val marc245Field = bibData
      .nonrepeatableVarfieldWithTag("245")
      .getOrElse(
        throw new ShouldNotTransformException("Could not find field 245 to create title")
      )

    val components =
      marc245Field.subfields
        .filter { sf =>
          Seq("a", "b", "c", "n", "p").contains { sf.tag }
        }
        .map { _.content }

    if (components.isEmpty) {
      throw new ShouldNotTransformException("No subfields in field 245 for constructing the title")
    }

    Some(components.mkString(" "))
  }
}
