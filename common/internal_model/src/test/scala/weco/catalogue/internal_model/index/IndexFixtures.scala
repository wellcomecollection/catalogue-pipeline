package weco.catalogue.internal_model.index

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.Index
import com.sksamuel.elastic4s.requests.common.VersionType.ExternalGte
import io.circe.Encoder
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{Assertion, Suite}
import weco.elasticsearch.model.IndexId
import weco.elasticsearch.test.fixtures.ElasticsearchFixtures
import weco.fixtures.TestWith
import weco.json.JsonUtil.toJson
import weco.catalogue.internal_model.image.{Image, ImageState}
import weco.catalogue.internal_model.work.{Work, WorkState}

trait IndexFixtures extends ElasticsearchFixtures { this: Suite =>

//  def withLocalWorksIndex[R](testWith: TestWith[Index, R]): R =
//    withLocalElasticsearchIndex[R](config = WorksIndexConfig.indexed) { index =>
//      testWith(index)
//    }

//  def withLocalIdentifiedWorksIndex[R](testWith: TestWith[Index, R]): R =
//    withLocalElasticsearchIndex[R](config = WorksIndexConfig.identified) {
//      index =>
//        testWith(index)
//    }

//  def withLocalMergedWorksIndex[R](testWith: TestWith[Index, R]): R =
//    withLocalElasticsearchIndex[R](config = WorksIndexConfig.merged) { index =>
//      testWith(index)
//    }
//
//  def withLocalDenormalisedWorksIndex[R](testWith: TestWith[Index, R]): R =
//    withLocalElasticsearchIndex[R](config = WorksIndexConfig.denormalised) {
//      index =>
//        testWith(index)
//    }
  def withLocalInitialImagesIndex[R](testWith: TestWith[Index, R]): R =
    withLocalElasticsearchIndex[R](config = ImagesIndexConfig.initial) {
      index =>
        testWith(index)
    }

  def withLocalAugmentedImageIndex[R](testWith: TestWith[Index, R]): R =
    withLocalElasticsearchIndex[R](config = ImagesIndexConfig.augmented) {
      index =>
        testWith(index)
    }

  def withLocalImagesIndex[R](testWith: TestWith[Index, R]): R =
    withLocalElasticsearchIndex[R](config = ImagesIndexConfig.indexed) {
      index =>
        testWith(index)
    }

  def assertElasticsearchEventuallyHasWork[State <: WorkState](
    index: Index,
    works: Work[State]*
  )(implicit enc: Encoder[Work[State]]): Seq[Assertion] = {
    implicit val id: IndexId[Work[State]] =
      (work: Work[State]) => work.id
    assertElasticsearchEventuallyHas(index, works: _*)
  }

  def assertElasticsearchEventuallyHasImage[State <: ImageState](
    index: Index,
    images: Image[State]*
  )(implicit enc: Encoder[Image[State]]): Seq[Assertion] = {
    implicit val id: IndexId[Image[State]] =
      (image: Image[State]) => image.id
    assertElasticsearchEventuallyHas(index, images: _*)
  }

  def insertIntoElasticsearch[State <: WorkState](
    index: Index,
    works: Work[State]*
  )(implicit encoder: Encoder[Work[State]]): Assertion = {
    val result = elasticClient.execute(
      bulk(
        works.map {
          work =>
            val jsonDoc = toJson(work).get
            indexInto(index.name)
              .version(work.version)
              .versionType(ExternalGte)
              .id(work.id)
              .doc(jsonDoc)
        }
      ).refreshImmediately
    )

    // With a large number of works this can take a long time
    // 30 seconds should be enough
    whenReady(result, Timeout(Span(30, Seconds))) {
      _ =>
        getSizeOf(index) shouldBe works.size
    }
  }

  def insertImagesIntoElasticsearch[State <: ImageState](
    index: Index,
    images: Image[State]*
  )(implicit encoder: Encoder[Image[State]]): Assertion = {
    val result = elasticClient.execute(
      bulk(
        images.map {
          image =>
            val jsonDoc = toJson(image).get

            indexInto(index.name)
              .version(image.version)
              .versionType(ExternalGte)
              .id(image.id)
              .doc(jsonDoc)
        }
      ).refreshImmediately
    )

    whenReady(result) {
      _ =>
        getSizeOf(index) shouldBe images.size
    }
  }
  def getSizeOf(index: Index): Long =
    elasticClient
      .execute { count(index.name) }
      .await
      .result
      .count
}
