package uk.ac.wellcome.transformer.common.worker

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.work.internal.result.Result
import WorkState.Source

trait Transformer[SourceData] {
  def apply(sourceData: SourceData, version: Int): Result[Work[Source]]
}
