package uk.ac.wellcome.models.index

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.indexes.IndexResponse
import org.scalacheck.ScalacheckShapeless._
import com.sksamuel.elastic4s.{ElasticError, Index, Response}
import org.scalacheck.Gen.chooseNum
import org.scalacheck.{Arbitrary, Shrink}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import uk.ac.wellcome.json.JsonUtil.toJson
import uk.ac.wellcome.json.utils.JsonAssertions
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.generators.WorkGenerators
import weco.catalogue.internal_model.generators.ImageGenerators
import weco.catalogue.internal_model.identifiers.{CanonicalId, IdState, SourceIdentifier}
import weco.catalogue.internal_model.locations.{AccessCondition, AccessStatus}
import weco.catalogue.internal_model.work._
import scala.concurrent.ExecutionContext.Implicits.global

import java.time.Instant
import scala.concurrent.Future

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
  implicit val noShrinkDenormalised = Shrink.shrinkAny[Work[WorkState.Denormalised]]
  implicit val noShrinkIdentified = Shrink.shrinkAny[Work[WorkState.Identified]]
  implicit val noShrinkIndexed = Shrink.shrinkAny[Work[WorkState.Indexed]]

  // We use this for the scalacheck of the java.time.Instant type
  // We could just import the library, but I might wait until we need more
  // Taken from here:
  // https://github.com/rallyhealth/scalacheck-ops/blob/master/core/src/main/scala/org/scalacheck/ops/time/ImplicitJavaTimeGenerators.scala
  implicit val arbInstant: Arbitrary[Instant] =
    Arbitrary {
      for {
        millis <- chooseNum(
          Instant.MIN.getEpochSecond,
          Instant.MAX.getEpochSecond)
        nanos <- chooseNum(Instant.MIN.getNano, Instant.MAX.getNano)
      } yield {
        Instant.ofEpochMilli(millis).plusNanos(nanos)
      }
    }

  // We have a rule that says SourceIdentifier isn't allowed to contain whitespace,
  // but sometimes scalacheck will happen to generate such a string, which breaks
  // tests in CI.  This generator is meant to create SourceIdentifiers that
  // don't contain whitespace.
  implicit val arbitrarySourceIdentifier: Arbitrary[SourceIdentifier] =
    Arbitrary {
      createSourceIdentifier
    }

  implicit val arbitraryCanonicalId: Arbitrary[CanonicalId] =
    Arbitrary {
      createCanonicalId
    }

  implicit val badObjectEncoder: Encoder[BadTestObject] = deriveEncoder

  describe("indexing different works with every type of WorkState") {
    it("WorkState.Source") {
      forAll { sourceWork: Work[WorkState.Source] =>
        withLocalIndex(SourceWorkIndexConfig) { index =>
          println(sourceWork)
          whenReady(indexObject(index, sourceWork)) { _ =>
            assertObjectIndexed(index, sourceWork)
          }
        }
      }
    }

    it("WorkState.Identified") {
      forAll { identifiedWork: Work[WorkState.Identified] =>
        withLocalIndex(IdentifiedWorkIndexConfig) { index =>
          whenReady(indexObject(index, identifiedWork)) { _ =>
            assertObjectIndexed(index, identifiedWork)
          }
        }
      }
    }

    it("WorkState.Merged") {
      forAll { mergedWork: Work[WorkState.Merged] =>
        withLocalIndex(MergedWorkIndexConfig) { index =>
          whenReady(indexObject(index, mergedWork)) { _ =>
            assertObjectIndexed(index, mergedWork)
          }
        }
      }
    }

    it("WorkState.Denormalised") {
      forAll { denormalisedWork: Work[WorkState.Denormalised] =>
        withLocalIndex(DenormalisedWorkIndexConfig) { index =>
          whenReady(indexObject(index, denormalisedWork)) { _ =>
            assertObjectIndexed(index, denormalisedWork)
          }
        }
      }
    }

    it("WorkState.Indexed") {
      def indexObject2[T](index: Index, t: T)(
        implicit encoder: Encoder[T]): Future[Response[IndexResponse]] = {
        val doc = toJson(t).get
        debug(s"ingesting: $doc")
        elasticClient
          .execute {
            indexInto(index.name).doc(doc)
          }
          .map { r =>
            println(r)
//            if (r.isError) {
//              error(s"Error from Elasticsearch: $r")
//            }
            r
          }
      }

      forAll { indexedWork: Work[WorkState.Indexed] =>
        withLocalIndex(IndexedWorkIndexConfig) { index =>
          whenReady(indexObject2(index, indexedWork)) { _ =>
            assertObjectIndexed(index, indexedWork)
          }
        }
      }
    }
  }

  // Possibly because the number of variations in the work model is too big,
  // a bug in the mapping related to person subjects wasn't caught by the above test.
  // So let's add a specific one
  it("puts a work with a person subject") {
    withLocalWorksIndex { index =>
      val sampleWork = identifiedWork().subjects(
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
      whenReady(indexObject(index, sampleWork)) { _ =>
        assertObjectIndexed(index, sampleWork)
      }
    }
  }

  // Possibly because the number of variations in the work model is too big,
  // a bug in the mapping related to accessConditions wasn't caught by the catch-all test above.
  it("puts a work with a access condition") {
    val accessCondition: AccessCondition = AccessCondition(
      status = Some(AccessStatus.Open),
      terms = Some("ask nicely"),
      to = Some("2014"))

    withLocalWorksIndex { index =>
      val sampleWork = identifiedWork().items(
        List(
          createIdentifiedItemWith(locations = List(createDigitalLocationWith(
            accessConditions = List(accessCondition))))))
      whenReady(indexObject(index, sampleWork)) { _ =>
        assertObjectIndexed(index, sampleWork)
      }
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
    withLocalWorksIndex { index =>
      val sampleWork = identifiedWork().collectionPath(collectionPath)
      whenReady(indexObject(index, sampleWork)) { _ =>
        assertObjectIndexed(index, sampleWork)
      }
    }
  }

  it("can ingest a work with an image") {
    withLocalWorksIndex { index =>
      val sampleWork = identifiedWork().imageData(
        List(createImageData.toIdentified)
      )
      whenReady(indexObject(index, sampleWork)) { _ =>
        assertObjectIndexed(index, sampleWork)
      }
    }
  }

  it("does not put an invalid work") {
    withLocalWorksIndex { index =>
      val badTestObject = BadTestObject(
        id = "id",
        weight = 5
      )

      whenReady(indexObject(index, badTestObject)) { response =>
        response.isError shouldBe true
        response.error shouldBe a[ElasticError]
      }
    }
  }

  it("puts a valid work using compression") {
    forAll { sampleWork: Work[WorkState.Identified] =>
      withLocalWorksIndex { index =>
        whenReady(indexObjectCompressed(index, sampleWork)) { _ =>
          assertObjectIndexed(index, sampleWork)
        }
      }
    }
  }
}
