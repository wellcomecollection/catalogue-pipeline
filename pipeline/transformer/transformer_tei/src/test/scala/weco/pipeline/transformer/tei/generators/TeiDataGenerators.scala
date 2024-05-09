package weco.pipeline.transformer.tei.generators

import weco.catalogue.internal_model.languages.Language
import weco.fixtures.RandomGenerators
import weco.pipeline.transformer.tei.TeiData

trait TeiDataGenerators extends RandomGenerators {
  def createTeiDataWith(
    id: String = randomAlphanumeric(),
    languages: List[Language] = Nil,
    nestedTeiData: List[TeiData] = Nil
  ): TeiData =
    TeiData(
      id = id,
      title = s"title-${randomAlphanumeric()}",
      languages = languages,
      nestedTeiData = nestedTeiData
    )
}
