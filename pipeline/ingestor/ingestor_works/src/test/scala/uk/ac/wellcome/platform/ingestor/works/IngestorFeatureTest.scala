package uk.ac.wellcome.platform.ingestor.works

import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.matchers.should.Matchers
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.elasticsearch.IndexedWorkIndexConfig
import uk.ac.wellcome.json.utils.JsonAssertions
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.pipeline_storage.ElasticIndexer
import uk.ac.wellcome.pipeline_storage.Indexable.workIndexable
import uk.ac.wellcome.models.Implicits._
import WorkState.{Denormalised, Indexed}
import com.sksamuel.elastic4s.Index
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.pipeline_storage.elastic.ElasticSourceRetriever

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
              assertElasticsearchEventuallyHasWork[Indexed](
                indexedIndex,
                WorkTransformer.deriveData(work))
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
              assertElasticsearchEventuallyHasWork[Indexed](
                indexedIndex,
                WorkTransformer.deriveData(work))
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
        IndexedWorkIndexConfig),
      retriever = new ElasticSourceRetriever[Work[Denormalised]](
        elasticClient,
        denormalisedIndex
      )
    )(testWith)
}
