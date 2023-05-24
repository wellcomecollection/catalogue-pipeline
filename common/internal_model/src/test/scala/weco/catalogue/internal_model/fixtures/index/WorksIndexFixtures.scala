package weco.catalogue.internal_model.fixtures.index
import com.sksamuel.elastic4s.Index
import weco.fixtures.Fixture

trait WorksIndexFixtures extends IndexFixturesBase {

  def withLocalSourceIndex[R]: Fixture[Index, R] =
    withLocalUnanalysedJsonStore[R]

  def withLocalIdentifiedWorksIndex[R]: Fixture[Index, R] = {
    withLocalElasticSearchIndex[R](config =
      getConfig(
        mappings = "mappings.works_identified.v1.json",
        analysis = "analysis.works_identified.v1.json"
      )
    )
  }

  def withLocalMergedWorksIndex[R]: Fixture[Index, R] = {
    withLocalElasticSearchIndex[R](config =
      getConfig(
        mappings = "mappings.works_merged.v1.json",
        analysis = "analysis.works_merged.v1.json"
      )
    )
  }

  def withLocalDenormalisedWorksIndex[R]: Fixture[Index, R] = {
    withLocalElasticSearchIndex[R](config = getConfig("mappings.empty.v1.json"))
  }

  def withLocalWorksIndex[R]: Fixture[Index, R] = {
    withLocalElasticSearchIndex[R](config =
      getConfig(
        mappings = "mappings.works_indexed.v1.json",
        analysis = "analysis.works_indexed.v1.json"
      )
    )
  }
}
