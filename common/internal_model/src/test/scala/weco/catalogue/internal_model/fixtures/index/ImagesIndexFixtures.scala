package weco.catalogue.internal_model.fixtures.index
import com.sksamuel.elastic4s.Index
import weco.fixtures.Fixture

trait ImagesIndexFixtures extends IndexFixturesBase {
  def withLocalInitialImagesIndex[R]: Fixture[Index, R] =
    withLocalUnanalysedJsonStore[R]

  def withLocalAugmentedImageIndex[R]: Fixture[Index, R] =
    withLocalUnanalysedJsonStore[R]

  def withLocalImagesIndex[R]: Fixture[Index, R] = {
    withLocalElasticSearchIndex[R](config =
      getConfig(
        mappings = "mappings.images_indexed.v1.json",
        analysis = "analysis.works_indexed.v1.json"
      )
    )
  }

}
