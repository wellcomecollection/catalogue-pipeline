package weco.catalogue.internal_model.fixtures.index

import com.sksamuel.elastic4s.Index
import org.scalatest.Suite
import weco.catalogue.internal_model.fixtures.elasticsearch.ElasticsearchFixtures
import weco.fixtures.Fixture

import scala.io.{Codec, Source}

trait IndexFixturesNew extends ElasticsearchFixtures {
  this: Suite =>

  private def getConfig(mappings: String) =
    s"""{"mappings":${Source
        .fromResource(mappings)(Codec.UTF8)
        .mkString}}"""

  private def getConfig(mappings: String, analysis: String) =
    s"""{"mappings":${Source
        .fromResource(mappings)(Codec.UTF8)
        .mkString},
        "settings":{"analysis":   
        ${Source
        .fromResource(analysis)(Codec.UTF8)
        .mkString}
        }}
        """

  def withLocalSourceIndex[R]: Fixture[Index, R] = {
    withLocalElasticSearchIndex[R](config = getConfig("mappings.empty.v1.json"))
  }

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
  //  def assertElasticsearchEventuallyHasWork[State <: WorkState](
  //    index: Index,
  //    works: Work[State]*
  //  )(implicit enc: Encoder[Work[State]]): Seq[Assertion] = {
  //    implicit val id: IndexId[Work[State]] =
  //      (work: Work[State]) => work.id
  //    assertElasticsearchEventuallyHas(index, works: _*)
  //  }
  //
  //  def assertElasticsearchEventuallyHasImage[State <: ImageState](
  //    index: Index,
  //    images: Image[State]*
  //  )(implicit enc: Encoder[Image[State]]): Seq[Assertion] = {
  //    implicit val id: IndexId[Image[State]] =
  //      (image: Image[State]) => image.id
  //    assertElasticsearchEventuallyHas(index, images: _*)
  //  }
  //
  //  def insertIntoElasticsearch[State <: WorkState](
  //    index: Index,
  //    works: Work[State]*
  //  )(implicit encoder: Encoder[Work[State]]): Assertion = {
  //    val result = elasticClient.execute(
  //      bulk(
  //        works.map { work =>
  //          val jsonDoc = toJson(work).get
  //          indexInto(index.name)
  //            .version(work.version)
  //            .versionType(ExternalGte)
  //            .id(work.id)
  //            .doc(jsonDoc)
  //        }
  //      ).refreshImmediately
  //    )
  //
  //    // With a large number of works this can take a long time
  //    // 30 seconds should be enough
  //    whenReady(result, Timeout(Span(30, Seconds))) { _ =>
  //      getSizeOf(index) shouldBe works.size
  //    }
  //  }
  //
  //  def insertImagesIntoElasticsearch[State <: ImageState](
  //    index: Index,
  //    images: Image[State]*
  //  )(implicit encoder: Encoder[Image[State]]): Assertion = {
  //    val result = elasticClient.execute(
  //      bulk(
  //        images.map { image =>
  //          val jsonDoc = toJson(image).get
  //
  //          indexInto(index.name)
  //            .version(image.version)
  //            .versionType(ExternalGte)
  //            .id(image.id)
  //            .doc(jsonDoc)
  //        }
  //      ).refreshImmediately
  //    )
  //
  //    whenReady(result) { _ =>
  //      getSizeOf(index) shouldBe images.size
  //    }
  //  }
  //  def getSizeOf(index: Index): Long =
  //    elasticClient
  //      .execute { count(index.name) }
  //      .await
  //      .result
  //      .count
}
