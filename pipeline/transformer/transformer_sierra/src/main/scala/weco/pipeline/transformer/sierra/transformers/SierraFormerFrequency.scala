package weco.pipeline.transformer.sierra.transformers

import weco.sierra.models.SierraQueryOps
import weco.sierra.models.data.SierraBibData

object SierraFormerFrequency extends SierraDataTransformer with SierraQueryOps {
  override type Output = List[String]

  // Populate wwork:description.
  //
  // We use MARC field "321".  We join ǂa and ǂb with a space.
  //
  //  - Join 321 ǂa and ǂb with a space.  These are the only MARC tags we use.
  //  - If the ǂu looks like a URL, we wrap it in <a> tags with the URL as the
  //    link text
  //  - Wrap resulting string in <p> tags
  //  - Join each occurrence of 520 into description
  //
  // Notes:
  //  - 321 is repeatable, and we do have records where it's used multiple times.
  //  - As of November 2022, we only use 321 subfields ǂa and ǂb.
  //
  // See https://www.loc.gov/marc/bibliographic/bd321.html
  //
  override def apply(bibData: SierraBibData): List[String] =
    bibData
      .varfieldsWithTag("321")
      .map(vf => vf.subfieldsWithTags("a", "b").map(_.content).mkString(" "))
}
