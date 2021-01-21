package uk.ac.wellcome.pipeline_storage

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.ExecutionContext.Implicits.global

import uk.ac.wellcome.models.work.generators.{ImageGenerators, WorkGenerators}
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.elasticsearch.{
  InitialImageIndexConfig,
  MergedWorkIndexConfig
}
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.elasticsearch.model.CanonicalId
import WorkState.Merged
import ImageState.Initial

class EitherIndexerTest
    extends AnyFunSpec
    with Matchers
    with ElasticsearchFixtures
    with WorkGenerators
    with ImageGenerators {

  implicit val workId: CanonicalId[Work.Visible[Merged]] =
    (work: Work[Merged]) => work.id
  implicit val imageId: CanonicalId[Image[Initial]] = (image: Image[Initial]) =>
    image.id

  val works = (1 to 3).map(_ => mergedWork()).toList
  val images = (1 to 3).map(_ => createImageData.toInitialImage).toList

  val worksAndImages = works.map(Left(_)) ++ images.map(Right(_))

  it("indexes a list of either works or images") {

    withLocalInitialImagesIndex { imageIndex =>
      withLocalMergedWorksIndex { workIndex =>
        val indexer = new EitherIndexer(
          leftIndexer = new ElasticIndexer[Work[Merged]](
            client = elasticClient,
            index = workIndex,
            config = MergedWorkIndexConfig
          ),
          rightIndexer = new ElasticIndexer[Image[Initial]](
            client = elasticClient,
            index = imageIndex,
            config = InitialImageIndexConfig
          )
        )

        whenReady(indexer(worksAndImages)) { result =>
          result shouldBe a[Right[_, _]]

          assertElasticsearchEventuallyHas(workIndex, works: _*)
          assertElasticsearchEventuallyHas(imageIndex, images: _*)
        }
      }
    }
  }

  it("indexes a list of only works") {
    withLocalInitialImagesIndex { imageIndex =>
      withLocalMergedWorksIndex { workIndex =>
        val indexer = new EitherIndexer(
          leftIndexer = new ElasticIndexer[Work[Merged]](
            client = elasticClient,
            index = workIndex,
            config = MergedWorkIndexConfig
          ),
          rightIndexer = new ElasticIndexer[Image[Initial]](
            client = elasticClient,
            index = imageIndex,
            config = InitialImageIndexConfig
          )
        )

        whenReady(indexer(works.map(Left(_)))) { result =>
          result shouldBe a[Right[_, _]]
          assertElasticsearchEventuallyHas(workIndex, works: _*)
        }
      }
    }
  }
}
