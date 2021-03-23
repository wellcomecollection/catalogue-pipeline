package weco.catalogue.transformer

import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.internal_model.work.Work
import weco.catalogue.transformer.result.Result

trait Transformer[SourceData] {
  def apply(sourceData: SourceData, version: Int): Result[Work[Source]] = ???

  def apply(id: String,
            sourceData: SourceData,
            version: Int): Result[Work[Source]] =
    apply(sourceData = sourceData, version = version)
}
