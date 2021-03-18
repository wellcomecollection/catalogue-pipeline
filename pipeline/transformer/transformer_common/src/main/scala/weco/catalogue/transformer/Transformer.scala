package weco.catalogue.transformer

import weco.catalogue.internal_model.work.WorkState.Source
import uk.ac.wellcome.models.work.internal.result.Result
import weco.catalogue.internal_model.work.Work

trait Transformer[SourceData] {
  def apply(sourceData: SourceData, version: Int): Result[Work[Source]] = ???

  def apply(id: String,
            sourceData: SourceData,
            version: Int): Result[Work[Source]] =
    apply(sourceData = sourceData, version = version)
}
