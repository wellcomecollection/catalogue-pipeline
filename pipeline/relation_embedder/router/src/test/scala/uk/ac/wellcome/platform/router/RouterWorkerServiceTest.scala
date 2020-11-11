package uk.ac.wellcome.platform.router

import org.scalatest.concurrent.Eventually
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.messaging.fixtures.{SNS, SQS}
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.models.work.internal.CollectionPath
import uk.ac.wellcome.pipeline_storage.ElasticRetriever

class RouterWorkerServiceTest extends AnyFunSpec with WorkGenerators with SQS with Akka
  with ElasticsearchFixtures with Eventually{

  def work(path: String) =
    mergedWork(createSourceIdentifierWith(value = path))
      .collectionPath(CollectionPath(path = path))
      .title(path)

  it("sends collectionPath to paths topic"){
    val work = work("a")
    withActorSystem { implicit as =>
      withLocalSqsQueuePair() { case QueuePair(queue, dlq) =>
        val messageSender = new MemoryMessageSender
          withSQSStream(queue) { stream =>
            withLocalMergedWorksIndex { mergedIndex =>
              insertIntoElasticsearch(mergedIndex, work)
              val service = new RouterWorkerService(stream, messageSender, new ElasticRetriever(elasticClient, mergedIndex))
              service.run()
              eventually{
                assertQueueEmpty(queue)
                assertQueueEmpty(dlq)
                messageSender.getMessages[CollectionPath]() should contain(CollectionPath("a"))
              }
            }
        }
      }
    }
  }


}
