package weco.catalogue.internal_model.fixtures.index
import com.sksamuel.elastic4s.Index
import weco.fixtures.Fixture

trait WorksIndexFixtures extends IndexFixturesBase {

  def withLocalSourceWorksIndex[R]: Fixture[Index, R] =
    withLocalUnanalysedJsonStore[R]

  def withLocalIdentifiedWorksIndex[R]: Fixture[Index, R] = {
    withLocalElasticSearchIndex[R](config =
      getConfig(
        mappings = "mappings.works_identified.2023-05-26.json",
        analysis = "analysis.works_identified.2023-05-26.json"
      )
    )
  }

  def withLocalDenormalisedWorksIndex[R]: Fixture[Index, R] = {
    withLocalElasticSearchIndex[R](config =
      getConfig(
        mappings = "mappings.works_denormalised.2025-08-14.json",
        analysis = "analysis.works_denormalised.2023-05-26.json"
      )
    )
  }

  def withLocalWorksIndex[R]: Fixture[Index, R] = {
    withLocalElasticSearchIndex[R](config =
      getConfig(
        mappings = "mappings.works_indexed.2024-11-14.json",
        analysis = "analysis.works_indexed.2024-11-14.json"
      )
    )
  }
}
