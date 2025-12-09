package weco.catalogue.internal_model.index

import com.sksamuel.elastic4s.ElasticDsl._
import org.scalacheck.ScalacheckShapeless._
import com.sksamuel.elastic4s.{ElasticClient, ElasticError, Index}
import org.scalacheck.Shrink
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.deriveEncoder
import org.scalatest.Assertion
import weco.json.utils.JsonAssertions
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.generators.ImageGenerators
import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.catalogue.internal_model.fixtures.index.IndexFixtures
import weco.json.JsonUtil._

class WorksIndexConfigTest
    extends AnyFunSpec
    with IndexFixtures
    with Matchers
    with JsonAssertions
    with ScalaCheckPropertyChecks
    with WorkGenerators
    with ImageGenerators {

  case class BadTestObject(
    id: String,
    weight: Int
  )

  // On failure, scalacheck tries to shrink to the smallest input that causes a failure.
  // With IdentifiedWork, that means that it never actually completes.
  implicit val noShrinkSource = Shrink.shrinkAny[Work[WorkState.Source]]
  implicit val noShrinkMerged = Shrink.shrinkAny[Work[WorkState.Merged]]
  implicit val noShrinkIdentified = Shrink.shrinkAny[Work[WorkState.Identified]]

  implicit val badObjectEncoder: Encoder[BadTestObject] = deriveEncoder

  describe("indexing different works with every type of WorkState") {
    it("WorkState.Source") {
      withLocalSourceWorksIndex {
        implicit index =>
          forAll {
            sourceWork: Work[WorkState.Source] =>
              assertWorkCanBeIndexed(sourceWork)
          }
      }
    }

    it("WorkState.Identified") {
      withLocalIdentifiedWorksIndex {
        implicit index =>
          forAll {
            identifiedWork: Work[WorkState.Identified] =>
              assertWorkCanBeIndexed(identifiedWork)
          }
      }
    }

    it("WorkState.Merged") {
      withLocalDenormalisedWorksIndex {
        implicit index =>
          forAll {
            mergedWork: Work[WorkState.Merged] =>
              assertWorkCanBeIndexed(mergedWork)
          }
      }
    }
  }

  it("does not put an invalid work") {
    withLocalWorksIndex {
      implicit index =>
        val notAWork = BadTestObject(
          id = "id",
          weight = 5
        )

        val response = indexWork(id = "id", work = notAWork)

        response.isError shouldBe true
        response.error shouldBe a[ElasticError]
    }
  }

  private def assertWorkCanBeIndexed[W <: Work[_ <: WorkState]](
    work: W,
    client: ElasticClient = elasticClient
  )(
    implicit index: Index,
    decoder: Decoder[W],
    encoder: Encoder[W]
  ): Assertion = {
    val response = indexWork(client, id = work.state.id, work = work)

    if (response.isError) {
      println(s"Error indexing work ${work.state.id}: ${response.error}")
    }

    assertWorkIsIndexed(client, id = work.state.id, work = work)
  }

  private def indexWork[W](
    client: ElasticClient = elasticClient,
    id: String,
    work: W
  )(implicit index: Index, encoder: Encoder[W]) =
    client.execute {
      indexInto(index).doc(toJson(work).get).id(id)
    }.await

  private def assertWorkIsIndexed[W](
    client: ElasticClient,
    id: String,
    work: W
  )(implicit index: Index, decoder: Decoder[W]) =
    eventually {
      whenReady(client.execute(get(index, id))) {
        getResponse =>
          getResponse.result.exists shouldBe true

          fromJson[W](getResponse.result.sourceAsString).get shouldBe work
      }
    }
}
