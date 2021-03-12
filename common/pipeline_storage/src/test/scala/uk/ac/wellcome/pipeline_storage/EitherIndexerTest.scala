package uk.ac.wellcome.pipeline_storage

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.ExecutionContext.Implicits.global
import uk.ac.wellcome.models.work.generators.{ImageGenerators, WorkGenerators}
import uk.ac.wellcome.models.index.{
  IndexFixtures,
  InitialImageIndexConfig,
  MergedWorkIndexConfig
}
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.elasticsearch.model.IndexId
import WorkState.Merged
import ImageState.Initial

class EitherIndexerTest
    extends AnyFunSpec
    with Matchers
    with IndexFixtures
    with WorkGenerators
    with ImageGenerators {

  implicit val workId: IndexId[Work[Merged]] =
    (work: Work[Merged]) => work.id
  implicit val imageId: IndexId[Image[Initial]] = (image: Image[Initial]) =>
    image.id

  val works: Seq[Work[Merged]] = (1 to 3).map(_ => mergedWork()).toList
  val images: Seq[Image[Initial]] =
    (1 to 3).map(_ => createImageData.toInitialImage).toList

  val worksAndImages: Seq[Either[Work[Merged], Image[Initial]]] = works.map(
    Left(_)) ++ images.map(Right(_))

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
