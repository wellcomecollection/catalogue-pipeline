package uk.ac.wellcome.platform.ingestor.works

import org.scalatest.Assertion
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.elasticsearch.IndexedWorkIndexConfig
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.models.work.internal.WorkState.{Denormalised, Indexed}
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.pipeline_storage.Indexable.workIndexable
import uk.ac.wellcome.pipeline_storage.{ElasticIndexer, ElasticRetriever}

import scala.concurrent.ExecutionContext.Implicits.global

class IngestorWorkerServiceTest
    extends AnyFunSpec
    with Matchers
    with IngestorFixtures
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
      sourceIdentifier = createSourceIdentifierWith(
        identifierType = IdentifierType("calm-altref-no")
      )
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
      sourceIdentifier = createSourceIdentifierWith(
        identifierType = IdentifierType("calm-altref-no")
      )
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

  private def assertWorksIndexedCorrectly(
    works: Work[Denormalised]*): Assertion =
    withLocalWorksIndex { indexedIndex =>
      withLocalDenormalisedWorksIndex { identifiedIndex =>
        insertIntoElasticsearch(identifiedIndex, works: _*)
        withLocalSqsQueuePair(visibilityTimeout = 10) {
          case QueuePair(queue, dlq) =>
            withWorkerService(
              queue,
              indexer = new ElasticIndexer[Work[Indexed]](
                elasticClient,
                indexedIndex,
                IndexedWorkIndexConfig),
              retriever = new ElasticRetriever[Work[Denormalised]](
                elasticClient,
                identifiedIndex
              )
            ) { _ =>
              works.map { work =>
                sendNotificationToSQS(queue = queue, body = work.id)
              }

              assertElasticsearchEventuallyHasWork[Indexed](
                index = indexedIndex,
                works.map(WorkTransformer.deriveData): _*)

              assertQueueEmpty(queue)
              assertQueueEmpty(dlq)
            }
        }
      }
    }
}
