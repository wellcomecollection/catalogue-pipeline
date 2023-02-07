package weco.pipeline.transformer.sierra.transformers

import weco.pipeline.transformer.sierra.exceptions.ShouldNotTransformException
import weco.sierra.models.SierraQueryOps
import weco.sierra.models.data.SierraBibData
import weco.sierra.models.marc.Subfield

object SierraTitle extends SierraDataTransformer with SierraQueryOps {

  type Output = Option[String]

  // Populate wwork:title.  The rules are as follows:
  //
  //    Join MARC field 245 subfields ǂa, ǂb, ǂc, ǂh, ǂn and ǂp with a space.
  //    They should be joined in the same order as the original subfields.
  //
  //    Remove anything in square brackets from ǂh; this is legacy data we don't
  //    want to expose.
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
        throw new ShouldNotTransformException(
          "Could not find field 245 to create title"
        )
      )

    val selectedSubfields =
      marc245Field.subfields
        .filter {
          sf =>
            Seq("a", "b", "c", "h", "n", "p").contains { sf.tag }
        }

    val components =
      selectedSubfields
        .filterNot {
          sf =>
            // We only care about subfield ǂh for joining punctuation.
            // If it's the last subfield, there's nothing to join it to, so
            // we remove it.
            //
            // Note: this code doesn't cover pathological cases (e.g. multiple
            // instances of subfield ǂh at the end of the record) because they
            // don't seem to occur in practice, and if they do we should fix
            // them in Sierra.  This code is deliberately simple.
            sf.tag == "h" && selectedSubfields.last == sf
        }
        .map {
          // This slightly convoluted regex is meant to remove anything
          // in square brackets, e.g.
          //
          //    "[electronic resource] :" ~> " :"
          //
          case Subfield("h", content) =>
            content
              .replaceAll("\\[[^\\]]+\\]", "")
              .trim

          case Subfield(_, content) => content
        }

    if (components.isEmpty) {
      throw new ShouldNotTransformException(
        "No subfields in field 245 for constructing the title"
      )
    }

    Some(components.mkString(" "))
  }
}
