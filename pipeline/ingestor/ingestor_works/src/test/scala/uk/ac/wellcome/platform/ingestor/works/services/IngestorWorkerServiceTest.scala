package uk.ac.wellcome.platform.ingestor.works.services

import org.scalatest.Assertion
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.elasticsearch.IdentifiedWorkIndexConfig
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.ingestor.common.fixtures.IngestorFixtures
import uk.ac.wellcome.pipeline_storage.ElasticIndexer
import uk.ac.wellcome.pipeline_storage.Indexable.workIndexable
import uk.ac.wellcome.models.Implicits._
import WorkState.Identified

import scala.concurrent.ExecutionContext.Implicits.global

class IngestorWorkerServiceTest
    extends AnyFunSpec
    with Matchers
    with IngestorFixtures
    with WorkGenerators {

  it("indexes a Miro identified Work") {
    val miroSourceIdentifier = createSourceIdentifier

    val work = identifiedWork(sourceIdentifier = miroSourceIdentifier)

    assertWorksIndexedCorrectly(work)
  }

  it("indexes a Sierra identified Work") {
    val work = identifiedWork(
      sourceIdentifier = createSierraSystemSourceIdentifier
    )

    assertWorksIndexedCorrectly(work)
  }

  it("indexes a Sierra identified invisible Work") {
    val work =
      identifiedWork(sourceIdentifier = createSierraSystemSourceIdentifier)
        .invisible()

    assertWorksIndexedCorrectly(work)
  }

  it("indexes a Sierra identified redirected Work") {
    val work =
      identifiedWork(sourceIdentifier = createSierraSystemSourceIdentifier)
        .redirected(
          IdState.Identified(
            canonicalId = createCanonicalId,
            sourceIdentifier = createSourceIdentifier
          ))

    assertWorksIndexedCorrectly(work)
  }

  it("indexes a mixture of Miro and Sierra works") {
    val miroWork1 = identifiedWork(
      sourceIdentifier = createMiroSourceIdentifier
    )
    val miroWork2 = identifiedWork(
      sourceIdentifier = createMiroSourceIdentifier
    )
    val sierraWork1 = identifiedWork(
      sourceIdentifier = createSierraSystemSourceIdentifier
    )
    val sierraWork2 = identifiedWork(
      sourceIdentifier = createSierraSystemSourceIdentifier
    )

    val works = List(miroWork1, miroWork2, sierraWork1, sierraWork2)

    assertWorksIndexedCorrectly(works: _*)
  }

  it("inserts a non Sierra- or Miro- identified work") {
    val work = identifiedWork(
      sourceIdentifier = createSourceIdentifierWith(
        identifierType = IdentifierType("calm-altref-no")
      )
    )

    assertWorksIndexedCorrectly(work)
  }

  it("indexes a mixture of Miro and Sierra, and otherly-identified Works") {
    val miroWork = identifiedWork(
      sourceIdentifier = createMiroSourceIdentifier
    )
    val sierraWork = identifiedWork(
      sourceIdentifier = createSierraSystemSourceIdentifier
    )
    val otherWork = identifiedWork(
      sourceIdentifier = createSourceIdentifierWith(
        identifierType = IdentifierType("calm-altref-no")
      )
    )

    val works = List(miroWork, sierraWork, otherWork)

    assertWorksIndexedCorrectly(works: _*)
  }

  it(
    "deletes works from the queue, including older versions of already ingested works") {
    val oldSierraWork = identifiedWork(
      sourceIdentifier = createSierraSystemSourceIdentifier
    )

    val newSierraWork =
      identifiedWork(sourceIdentifier = oldSierraWork.sourceIdentifier)
        .withVersion(oldSierraWork.version + 1)

    val works = List(newSierraWork, oldSierraWork)

    assertWorksIndexedCorrectly(works: _*)
  }

  private def assertWorksIndexedCorrectly(works: Work[Identified]*): Assertion =
    withLocalWorksIndex { index =>
      withLocalSqsQueuePair(visibilityTimeout = 10) {
        case QueuePair(queue, dlq) =>
          withWorkerService(
            queue,
            index,
            new ElasticIndexer[Work[Identified]](
              elasticClient,
              index,
              IdentifiedWorkIndexConfig)) { _ =>
            works.map { work =>
              sendMessage[Work[Identified]](queue = queue, obj = work)
            }

            assertElasticsearchEventuallyHasWork(index = index, works: _*)

            assertQueueEmpty(queue)
            assertQueueEmpty(dlq)
          }
      }
    }
}
