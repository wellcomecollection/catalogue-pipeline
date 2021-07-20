package weco.catalogue.internal_model.index

import org.scalacheck.ScalacheckShapeless._
import com.sksamuel.elastic4s.ElasticError
import org.scalacheck.Gen.chooseNum
import org.scalacheck.{Arbitrary, Shrink}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import weco.json.utils.JsonAssertions
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.generators.ImageGenerators
import weco.catalogue.internal_model.identifiers.{
  CanonicalId,
  IdState,
  SourceIdentifier
}
import weco.catalogue.internal_model.languages.Language
import weco.catalogue.internal_model.locations.{
  AccessCondition,
  AccessMethod,
  AccessStatus
}
import weco.catalogue.internal_model.work._
import weco.catalogue.internal_model.work.generators.WorkGenerators

import java.time.Instant

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

  // We have a rule that says language codes should be exactly 3 characters long
  implicit val arbitraryLanguage: Arbitrary[Language] =
    Arbitrary {
      Language(
        id = randomAlphanumeric(length = 3),
        label = randomAlphanumeric())
    }

  implicit val badObjectEncoder: Encoder[BadTestObject] = deriveEncoder

  describe("indexing different works with every type of WorkState") {
    it("WorkState.Source") {
      forAll { sourceWork: Work[WorkState.Source] =>
        withLocalIndex(WorksIndexConfig.source) { index =>
          println(sourceWork)
          whenReady(indexObject(index, sourceWork)) { _ =>
            assertObjectIndexed(index, sourceWork)
          }
        }
      }
    }

    it("WorkState.Identified") {
      forAll { identifiedWork: Work[WorkState.Identified] =>
        withLocalIndex(WorksIndexConfig.identified) { index =>
          whenReady(indexObject(index, identifiedWork)) { _ =>
            assertObjectIndexed(index, identifiedWork)
          }
        }
      }
    }

    it("WorkState.Merged") {
      forAll { mergedWork: Work[WorkState.Merged] =>
        withLocalIndex(WorksIndexConfig.merged) { index =>
          whenReady(indexObject(index, mergedWork)) { _ =>
            assertObjectIndexed(index, mergedWork)
          }
        }
      }
    }

    it("WorkState.Denormalised") {
      forAll { denormalisedWork: Work[WorkState.Denormalised] =>
        withLocalIndex(WorksIndexConfig.denormalised) { index =>
          whenReady(indexObject(index, denormalisedWork)) { _ =>
            assertObjectIndexed(index, denormalisedWork)
          }
        }
      }
    }

    it("WorkState.Indexed") {
      forAll { indexedWork: Work[WorkState.Indexed] =>
        withLocalIndex(WorksIndexConfig.ingested) { index =>
          whenReady(indexObject(index, indexedWork)) { _ =>
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
      method = AccessMethod.OnlineRequest,
      status = AccessStatus.Open)

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
