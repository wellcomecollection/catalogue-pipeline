package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.platform.transformer.sierra.source.{
  SierraBibData,
  SierraQueryOps
}

object SierraDescription extends SierraDataTransformer with SierraQueryOps {

  type Output = Option[String]

  // Populate wwork:description.
  //
  // We use MARC field "520".  Rules:
  //
  //  - Join 520 ǂa and ǂb with a space
  //  - Wrap resulting string in <p> tags
  //  - Join each occurrence of 520 into description
  //
  // Notes:
  //  - Both ǂa (summary) and ǂb (expansion of summary note) are
  //    non-repeatable subfields.
  //  - We never expect to see a record with $b but not $a.
  //
  // https://www.loc.gov/marc/bibliographic/bd520.html
  //
  def apply(bibData: SierraBibData): Option[String] = {
    val description = bibData
      .varfieldsWithTag("520")
      .map { vf =>
        val summary = vf.nonrepeatableSubfieldWithTag("a").map { _.content }
        val expansion = vf.nonrepeatableSubfieldWithTag("b").map { _.content }

        Seq(summary, expansion).flatten.mkString(" ")
      }
      .map { description => s"<p>$description</p>" }
      .mkString("\n")

    if (description.nonEmpty) Some(description) else None
  }
}
