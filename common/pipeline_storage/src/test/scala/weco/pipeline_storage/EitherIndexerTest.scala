package weco.pipeline_storage

import com.sksamuel.elastic4s.Index
import io.circe.Encoder
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.ExecutionContext.Implicits.global
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.fixtures.index.IndexFixtures
import weco.elasticsearch.model.IndexId
import weco.catalogue.internal_model.work.WorkState.Merged
import weco.catalogue.internal_model.generators.ImageGenerators
import weco.catalogue.internal_model.image.Image
import weco.catalogue.internal_model.image.ImageState.Initial
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.fixtures.TestWith
import weco.pipeline_storage.elastic.ElasticIndexer

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

  val worksAndImages: Seq[Either[Work[Merged], Image[Initial]]] =
    works.map(Left(_)) ++ images.map(Right(_))

  def withEitherIndexer[L: Indexable, R: Indexable](
    testWith: TestWith[(EitherIndexer[L, R], Index, Index), Unit]
  )(
    implicit leftEncoder: Encoder[L],
    rightEncoder: Encoder[R]
  ): Unit = {
    withLocalInitialImagesIndex {
      imageIndex =>
        withLocalDenormalisedWorksIndex {
          workIndex =>
            val indexer = new EitherIndexer(
              leftIndexer = new ElasticIndexer[L](
                client = elasticClient,
                index = workIndex
              ),
              rightIndexer = new ElasticIndexer[R](
                client = elasticClient,
                index = imageIndex
              )
            )
            testWith((indexer, workIndex, imageIndex))
        }
    }

  }

  it("indexes a list of either works or images") {
    withEitherIndexer[Work[Merged], Image[Initial]] {
      case (indexer, workIndex, imageIndex) =>
        whenReady(indexer(worksAndImages)) {
          result =>
            result shouldBe a[Right[_, _]]
            assertElasticsearchEventuallyHas(workIndex, works: _*)
            assertElasticsearchEventuallyHas(imageIndex, images: _*)
        }
    }
  }

  it("indexes a list of only works") {
    withEitherIndexer[Work[Merged], Image[Initial]] {
      case (indexer, workIndex, _) =>
        whenReady(indexer(works.map(Left(_)))) {
          result =>
            result shouldBe a[Right[_, _]]
            assertElasticsearchEventuallyHas(workIndex, works: _*)
        }
    }
  }

  it("indexes a list of only images") {
    withEitherIndexer[Work[Merged], Image[Initial]] {
      case (indexer, _, imageIndex) =>
        whenReady(indexer(images.map(Right(_)))) {
          result =>
            result shouldBe a[Right[_, _]]
            assertElasticsearchEventuallyHas(imageIndex, images: _*)
        }
    }
  }

  it("returns successfully when there are neither works nor images") {
    withEitherIndexer[Work[Merged], Image[Initial]] {
      case (indexer, _, _) =>
        whenReady(indexer(Nil)) {
          result =>
            result shouldBe a[Right[_, _]]
        }
    }
  }

}
