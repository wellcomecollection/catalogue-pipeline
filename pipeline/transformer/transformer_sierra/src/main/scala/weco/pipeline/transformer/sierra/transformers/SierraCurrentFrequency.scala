package weco.pipeline.transformer.sierra.transformers

import weco.sierra.models.SierraQueryOps
import weco.sierra.models.data.SierraBibData

object SierraCurrentFrequency
    extends SierraDataTransformer
    with SierraQueryOps {
  override type Output = Option[String]

  // We use MARC field "310".  We join ǂa and ǂb with a space.
  //
  // Notes:
  //  - Although 310 is theoretically repeatable, in practice we use it only once
  //    on all but a handful of records.  In those cases, join with a space.
  //  - As of November 2022, we only use 310 subfields ǂa and ǂb.
  //
  // See https://www.loc.gov/marc/bibliographic/bd310.html
  //
  override def apply(bibData: SierraBibData): Option[String] = {
    val result = bibData
      .varfieldsWithTag("310")
      .map(vf => vf.subfieldsWithTags("a", "b").map(_.content).mkString(" "))
      .mkString(" ")

    if (result.isEmpty) None else Some(result)
  }
}
