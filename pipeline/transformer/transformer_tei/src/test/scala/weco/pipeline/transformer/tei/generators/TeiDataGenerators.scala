package weco.pipeline.transformer.tei.generators

import weco.fixtures.RandomGenerators
import weco.pipeline.transformer.result.Result
import weco.pipeline.transformer.tei.TeiData

trait TeiDataGenerators extends RandomGenerators {
  def createTeiDataWith(id: String, nestedTeiData: Result[List[TeiData]] = Right(Nil)): TeiData =
    TeiData(
      id = id,
      title = s"title-${randomAlphanumeric()}",
      nestedTeiData = nestedTeiData
    )
}
