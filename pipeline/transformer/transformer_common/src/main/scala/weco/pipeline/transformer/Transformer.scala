package weco.pipeline.transformer

import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.internal_model.work.Work
import weco.pipeline.transformer.result.Result

trait Transformer[SourceData] {

  def apply(
    id: String,
    sourceData: SourceData,
    version: Int
  ): Result[Work[Source]]
}
