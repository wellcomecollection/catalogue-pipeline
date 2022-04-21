package weco.pipeline.ingestor.works

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Denormalised
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.pipeline.ingestor.works.fixtures.WorksIngestorFixtures

class IngestorWorkerServiceTest
    extends AnyFunSpec
    with Matchers
    with WorksIngestorFixtures
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

  it("indexes a mixture of Miro and Sierra, and otherly-denormalised Works") {
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
    withLocalSqsQueue() { queue =>
      withWorksIngestor(queue, existingWorks = works) { index =>
        works.map { work =>
          sendNotificationToSQS(queue = queue, body = work.id)
        }

        works.foreach {
          assertWorkIndexed(index, _)
        }
      }
    }
}
