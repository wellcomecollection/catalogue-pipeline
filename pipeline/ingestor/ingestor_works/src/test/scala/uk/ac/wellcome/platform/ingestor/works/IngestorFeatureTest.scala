package uk.ac.wellcome.platform.ingestor.works

import com.sksamuel.elastic4s.Index
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.fixtures.TestWith
import weco.json.utils.JsonAssertions
import weco.messaging.fixtures.SQS.Queue
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.index.WorksIndexConfig
import weco.pipeline_storage.Indexable.workIndexable
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.{Denormalised, Indexed}
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.pipeline_storage.elastic.{ElasticIndexer, ElasticSourceRetriever}

import scala.concurrent.ExecutionContext.Implicits.global

class IngestorFeatureTest
    extends AnyFunSpec
    with Matchers
    with JsonAssertions
    with IngestorFixtures
    with WorkGenerators {

  it("ingests a Miro work") {
    val work = denormalisedWork(
      sourceIdentifier = createMiroSourceIdentifier
    )

    withLocalWorksIndex { indexedIndex =>
      withLocalDenormalisedWorksIndex { denormalisedIndex =>
        insertIntoElasticsearch(denormalisedIndex, work)
        withLocalSqsQueue() { queue =>
          withWorkIngestorWorkerService(queue, indexedIndex, denormalisedIndex) {
            _ =>
              sendNotificationToSQS(queue = queue, body = work.id)
              assertWorkIndexed(indexedIndex, work)
          }
        }
      }
    }
  }

  it("ingests a Sierra work") {
    val work = denormalisedWork(
      sourceIdentifier = createSierraSystemSourceIdentifier
    )

    withLocalWorksIndex { indexedIndex =>
      withLocalDenormalisedWorksIndex { denormalisedIndex =>
        insertIntoElasticsearch(denormalisedIndex, work)
        withLocalSqsQueue() { queue =>
          withWorkIngestorWorkerService(queue, indexedIndex, denormalisedIndex) {
            _ =>
              sendNotificationToSQS(queue = queue, body = work.id)
              assertWorkIndexed(indexedIndex, work)
          }
        }
      }
    }
  }

  def withWorkIngestorWorkerService[R](queue: Queue,
                                       indexedIndex: Index,
                                       denormalisedIndex: Index)(
    testWith: TestWith[WorkIngestorWorkerService[String], R]): R =
    withWorkerService(
      queue,
      indexer = new ElasticIndexer[Work[Indexed]](
        elasticClient,
        indexedIndex,
        WorksIndexConfig.ingested),
      retriever = new ElasticSourceRetriever[Work[Denormalised]](
        elasticClient,
        denormalisedIndex
      )
    )(testWith)
}
