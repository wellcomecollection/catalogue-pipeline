package weco.catalogue.transformer

import uk.ac.wellcome.models.work.internal.Work
import uk.ac.wellcome.models.work.internal.WorkState.Source
import uk.ac.wellcome.models.work.internal.result.Result

trait Transformer[SourceData] {
  def apply(sourceData: SourceData, version: Int): Result[Work[Source]]
}
