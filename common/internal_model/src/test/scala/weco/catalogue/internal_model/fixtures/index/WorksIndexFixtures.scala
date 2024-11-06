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

  def withLocalMergedWorksIndex[R]: Fixture[Index, R] = {
    withLocalElasticSearchIndex[R](config =
      getConfig(
        mappings = "mappings.works_merged.2023-05-26.json",
        analysis = "analysis.works_merged.2023-05-26.json"
      )
    )
  }

  def withLocalDenormalisedWorksIndex[R]: Fixture[Index, R] =
    withLocalUnanalysedJsonStore[R]

  def withLocalWorksIndex[R]: Fixture[Index, R] = {
    withLocalElasticSearchIndex[R](config =
      getConfig(
        mappings = "mappings.works_indexed.2024-11-06.json",
        analysis = "analysis.works_indexed.2024-08-20.json"
      )
    )
  }
}
