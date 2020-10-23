package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.platform.transformer.sierra.exceptions.ShouldNotTransformException
import uk.ac.wellcome.platform.transformer.sierra.source.{
  SierraBibData,
  SierraQueryOps
}
import uk.ac.wellcome.sierra_adapter.model.SierraBibNumber

object SierraTitle extends SierraDataTransformer with SierraQueryOps {

  type Output = Option[String]

  // Populate wwork:title.  The rules are as follows:
  //
  //    Join MARC field 245 subfields $a, $b and $c with a space.
  //
  // MARC 245 is non-repeatable, as are these three subfields.
  // http://www.loc.gov/marc/bibliographic/bd245.html
  def apply(bibId: SierraBibNumber, bibData: SierraBibData): Option[String] = {
    val marc245Field = bibData
      .nonrepeatableVarfieldWithTag("245")
      .getOrElse(
        throw new ShouldNotTransformException("Could not find varField 245!")
      )

    val components: Seq[String] =
      Seq("a", "b", "c")
        .flatMap { marc245Field.nonrepeatableSubfieldWithTag(_) }
        .map { _.content }

    if (components.isEmpty) {
      throw new ShouldNotTransformException("No fields to construct title!")
    }

    Some(components.mkString(" "))
  }
}
