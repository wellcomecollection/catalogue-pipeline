package weco.catalogue.internal_model.index

import com.sksamuel.elastic4s.ElasticDsl._
import org.scalacheck.ScalacheckShapeless._
import com.sksamuel.elastic4s.{ElasticClient, ElasticError, Index}
import org.scalacheck.Shrink
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.deriveEncoder
import org.scalatest.Assertion
import weco.json.utils.JsonAssertions
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.generators.ImageGenerators
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.locations.{AccessCondition, AccessMethod, AccessStatus}
import weco.catalogue.internal_model.work._
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.json.JsonUtil._

class WorksIndexConfigTest
    extends AnyFunSpec
    with IndexFixtures
    with ScalaFutures
    with Eventually
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
  implicit val noShrinkDenormalised =
    Shrink.shrinkAny[Work[WorkState.Denormalised]]
  implicit val noShrinkIdentified = Shrink.shrinkAny[Work[WorkState.Identified]]
  implicit val noShrinkIndexed = Shrink.shrinkAny[Work[WorkState.Indexed]]

  implicit val badObjectEncoder: Encoder[BadTestObject] = deriveEncoder

  describe("indexing different works with every type of WorkState") {
    it("WorkState.Source") {
      withLocalIndex(WorksIndexConfig.source) { implicit index =>
        forAll { sourceWork: Work[WorkState.Source] =>
          assertWorkCanBeIndexed(sourceWork)
        }
      }
    }

    it("WorkState.Identified") {
      withLocalIndex(WorksIndexConfig.identified) { implicit index =>
        forAll { identifiedWork: Work[WorkState.Identified] =>
          assertWorkCanBeIndexed(identifiedWork)
        }
      }
    }

    it("WorkState.Merged") {
      withLocalIndex(WorksIndexConfig.merged) { implicit index =>
        forAll { mergedWork: Work[WorkState.Merged] =>
          assertWorkCanBeIndexed(mergedWork)
        }
      }
    }

    it("WorkState.Denormalised") {
      withLocalIndex(WorksIndexConfig.denormalised) { implicit index =>
        forAll { denormalisedWork: Work[WorkState.Denormalised] =>
          assertWorkCanBeIndexed(denormalisedWork)
        }
      }
    }

    it("WorkState.Indexed") {
      withLocalIndex(WorksIndexConfig.ingested) { implicit index =>
        forAll { indexedWork: Work[WorkState.Indexed] =>
          assertWorkCanBeIndexed(indexedWork)
        }
      }
    }
  }

  // Possibly because the number of variations in the work model is too big,
  // a bug in the mapping related to person subjects wasn't caught by the above test.
  // So let's add a specific one
  it("puts a work with a person subject") {
    val workWithSubjects = identifiedWork().subjects(
      List(
        Subject(
          id = IdState.Unidentifiable,
          label = "Daredevil",
          concepts = List(
            Person(
              id = IdState.Unidentifiable,
              label = "Daredevil",
              prefix = Some("Superhero"),
              numeration = Some("I")
            )
          )
        )
      )
    )

    withLocalWorksIndex { implicit index =>
      assertWorkCanBeIndexed(workWithSubjects)
    }
  }

  // Possibly because the number of variations in the work model is too big,
  // a bug in the mapping related to accessConditions wasn't caught by the catch-all test above.
  it("puts a work with a access condition") {
    val accessCondition: AccessCondition = AccessCondition(
      method = AccessMethod.OnlineRequest,
      status = AccessStatus.Open)

    val workWithAccessConditions = identifiedWork().items(
      List(
        createIdentifiedItemWith(locations = List(createDigitalLocationWith(
          accessConditions = List(accessCondition))))))

    withLocalWorksIndex { implicit index =>
      assertWorkCanBeIndexed(workWithAccessConditions)
    }
  }

  // Because we use copy_to and some other index functionality
  // the potentially fails at PUT index time, we urn this test
  // e.g. copy_to was previously set to `collection.depth`
  // which would not work as the mapping is strict and `collection`
  // only exists at the `data.collectionPath` level
  it("puts a work with a collection") {
    val collectionPath = CollectionPath(
      path = "PATH/FOR/THE/COLLECTION",
      label = Some("PATH/FOR/THE/COLLECTION")
    )

    val work = identifiedWork().collectionPath(collectionPath)

    withLocalWorksIndex { implicit index =>
      assertWorkCanBeIndexed(work)
    }
  }

  it("can ingest a work with an image") {
    val workWithImage = identifiedWork().imageData(
      List(createImageData.toIdentified)
    )

    withLocalWorksIndex { implicit index =>
      assertWorkCanBeIndexed(workWithImage)
    }
  }

  it("does not put an invalid work") {
    withLocalWorksIndex { implicit index =>
      val notAWork = BadTestObject(
        id = "id",
        weight = 5
      )

      val response = indexWork(index = index, id = "id", work = notAWork)

      response.isError shouldBe true
      response.error shouldBe a[ElasticError]
    }
  }

  it("puts a valid work using compression") {
    withLocalWorksIndex { implicit index =>
      forAll { identifiedWork: Work[WorkState.Identified] =>
        assertWorkCanBeIndexed(client = elasticClientWithCompression, work = identifiedWork)
      }
    }
  }

  private def assertWorkCanBeIndexed[W <: Work[_ <: WorkState]](work: W, client: ElasticClient = elasticClient)(implicit index: Index, decoder: Decoder[W], encoder: Encoder[W]): Assertion = {
    indexWork(client, index = index, id = work.state.id, work = work)
    assertWorkIsIndexed(client, index = index, id = work.state.id, work = work)
  }

  private def indexWork[W](client: ElasticClient = elasticClient, index: Index, id: String, work: W)(implicit encoder: Encoder[W]) =
    client
      .execute {
        indexInto(index).doc(toJson(work).get).id(id)
      }
      .await

  private def assertWorkIsIndexed[W](client: ElasticClient, index: Index, id: String, work: W)(implicit decoder: Decoder[W]) =
    eventually {
      whenReady(client.execute(get(index, id))) { getResponse =>
        getResponse.result.exists shouldBe true

        fromJson[W](getResponse.result.sourceAsString).get shouldBe work
      }
    }
}
