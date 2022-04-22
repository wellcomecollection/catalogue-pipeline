package weco.pipeline.ingestor.works

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.generators.ImageGenerators
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.locations.{AccessCondition, AccessMethod, AccessStatus}
import weco.catalogue.internal_model.work.WorkState.Denormalised
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.catalogue.internal_model.work.{CollectionPath, Person, Subject, Work}
import weco.messaging.fixtures.SQS.QueuePair
import weco.pipeline.ingestor.works.fixtures.WorksIngestorFixtures

class IngestorWorkerServiceTest
    extends AnyFunSpec
    with Matchers
    with WorksIngestorFixtures
    with ImageGenerators
    with WorkGenerators {

  it("indexes a Miro denormalised Work") {
    val miroSourceIdentifier = createSourceIdentifier

    val work = denormalisedWork(sourceIdentifier = miroSourceIdentifier)

    assertWorksIndexedCorrectly(work)
  }

  it("indexes a Sierra denormalised Work") {
    val work = denormalisedWork(
      sourceIdentifier = createSierraSystemSourceIdentifier
    )

    assertWorksIndexedCorrectly(work)
  }

  it("indexes a Sierra denormalised invisible Work") {
    val work =
      denormalisedWork(sourceIdentifier = createSierraSystemSourceIdentifier)
        .invisible()

    assertWorksIndexedCorrectly(work)
  }

  it("indexes a Sierra denormalised redirected Work") {
    val work =
      denormalisedWork(sourceIdentifier = createSierraSystemSourceIdentifier)
        .redirected(
          IdState.Identified(
            canonicalId = createCanonicalId,
            sourceIdentifier = createSourceIdentifier
          ))

    assertWorksIndexedCorrectly(work)
  }

  it("indexes a mixture of Miro and Sierra works") {
    val miroWork1 = denormalisedWork(
      sourceIdentifier = createMiroSourceIdentifier
    )
    val miroWork2 = denormalisedWork(
      sourceIdentifier = createMiroSourceIdentifier
    )
    val sierraWork1 = denormalisedWork(
      sourceIdentifier = createSierraSystemSourceIdentifier
    )
    val sierraWork2 = denormalisedWork(
      sourceIdentifier = createSierraSystemSourceIdentifier
    )

    val works = List(miroWork1, miroWork2, sierraWork1, sierraWork2)

    assertWorksIndexedCorrectly(works: _*)
  }

  it("inserts a non Sierra- or Miro- denormalised work") {
    val work = denormalisedWork(
      sourceIdentifier = createCalmSourceIdentifier
    )

    assertWorksIndexedCorrectly(work)
  }

  it("indexes a mixture of Miro and Sierra, and other-denormalised Works") {
    val miroWork = denormalisedWork(
      sourceIdentifier = createMiroSourceIdentifier
    )
    val sierraWork = denormalisedWork(
      sourceIdentifier = createSierraSystemSourceIdentifier
    )
    val otherWork = denormalisedWork(
      sourceIdentifier = createCalmSourceIdentifier
    )

    val works = List(miroWork, sierraWork, otherWork)

    assertWorksIndexedCorrectly(works: _*)
  }

  it("indexes a work with images") {
    val workWithImage = denormalisedWork()
      .imageData(List(createImageData.toIdentified))

    assertWorksIndexedCorrectly(workWithImage)
  }

  it("indexes an invisible work") {
    val work = denormalisedWork().invisible()

    assertWorksIndexedCorrectly(work)
  }

  // Because we use copy_to and some other index functionality
  // the potentially fails at PUT index time, we urn this test
  // e.g. copy_to was previously set to `collection.depth`
  // which would not work as the mapping is strict and `collection`
  // only exists at the `data.collectionPath` level
  it("indexes a work with a collection") {
    val collectionPath = CollectionPath(
      path = "PATH/FOR/THE/COLLECTION",
      label = Some("PATH/FOR/THE/COLLECTION")
    )

    val work = denormalisedWork().collectionPath(collectionPath)

    assertWorksIndexedCorrectly(work)
  }

  // Possibly because the number of variations in the work model is too big,
  // a bug in the mapping related to person subjects wasn't caught by other tests.
  // So let's add a specific one.
  it("puts a work with a person subject") {
    val workWithSubjects = denormalisedWork().subjects(
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

    assertWorksIndexedCorrectly(workWithSubjects)
  }

  // Possibly because the number of variations in the work model is too big,
  // a bug in the mapping related to accessConditions wasn't caught by the catch-all test above.
  it("puts a work with a access condition") {
    val accessCondition: AccessCondition = AccessCondition(
      method = AccessMethod.OnlineRequest,
      status = AccessStatus.Open)

    val workWithAccessConditions = denormalisedWork().items(
      List(createIdentifiedItemWith(locations = List(
        createDigitalLocationWith(accessConditions = List(accessCondition))))))

    assertWorksIndexedCorrectly(workWithAccessConditions)
  }

  it(
    "deletes works from the queue, including older versions of already ingested works") {
    val oldSierraWork = denormalisedWork(
      sourceIdentifier = createSierraSystemSourceIdentifier
    )

    val newSierraWork =
      denormalisedWork(sourceIdentifier = oldSierraWork.sourceIdentifier)
        .withVersion(oldSierraWork.version + 1)

    val works = List(newSierraWork, oldSierraWork)

    assertWorksIndexedCorrectly(works: _*)
  }

  private def assertWorksIndexedCorrectly(works: Work[Denormalised]*): Unit =
    withLocalSqsQueuePair() { case QueuePair(queue, dlq) =>
      withWorksIngestor(queue, existingWorks = works) { index =>
        works.map { work =>
          sendNotificationToSQS(queue = queue, body = work.id)
        }

        works.foreach {
          assertWorkIndexed(index, _)
        }

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
        }
      }
    }
}
