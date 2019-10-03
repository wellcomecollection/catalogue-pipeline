package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.models.transformable.sierra.SierraBibNumber
import uk.ac.wellcome.platform.transformer.sierra.source.{
  SierraBibData,
  SierraQueryOps
}

object SierraNotes extends SierraTransformer with SierraQueryOps {

  type Output = List[String]

  def apply(bibId: SierraBibNumber, bibData: SierraBibData) =
    bibData
      .varfieldsWithTags("500", "501", "504", "518", "536", "545", "547", "562")
      .subfieldContents
}
