package uk.ac.wellcome.platform.ingestor.works

import scala.concurrent.{ExecutionContext, Future}
import akka.Done
import software.amazon.awssdk.services.sqs.model.Message
import grizzled.slf4j.Logging

import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.pipeline_storage.{Indexer, Retriever}
import uk.ac.wellcome.platform.ingestor.common.models.IngestorConfig
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.typesafe.Runnable
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.work.internal._
import WorkState.{Denormalised, Indexed}

class WorkIngestorWorkerService(
  ingestorConfig: IngestorConfig,
  msgStream: SQSStream[NotificationMessage],
  workRetriever: Retriever[Work[Denormalised]],
  workIndexer: Indexer[Work[Indexed]],
  transformBeforeIndex: Work[Denormalised] => Work[Indexed] =
    WorkTransformer.deriveData,
)(implicit
  ec: ExecutionContext)
    extends Runnable
    with Logging {

  case class Bundle(message: Message, key: String)

  def run(): Future[Done] =
    for {
      _ <- workIndexer.init()
      result <- runStream()
    } yield result

  private def runStream(): Future[Done] =
    msgStream.runStream(
      this.getClass.getSimpleName,
      source => {
        source
          .map {
            case (msg, notificationMessage) =>
              Bundle(msg, notificationMessage.body)
          }
          .groupedWithin(ingestorConfig.batchSize, ingestorConfig.flushInterval)
          .mapAsyncUnordered(10) { bundles =>
            processMessages(bundles.toList)
          }
          .mapConcat(identity)
          .map(_.message)
      }
    )

  private def processMessages(bundles: List[Bundle]): Future[List[Bundle]] =
    for {
      works <- workRetriever(bundles.map(_.key)).map { retrieverResult =>
        if (retrieverResult.notFound.nonEmpty) {
          error(s"Failed retrieving works: ${retrieverResult.notFound}")
        }
        retrieverResult.found.values.toList
      }
      transformedWorks = works.map(transformBeforeIndex)
      indexResult <- workIndexer.index(documents = transformedWorks)
    } yield {
      val failedWorks = indexResult.left.getOrElse(Nil).map(_.id)
      bundles.filterNot {
        case Bundle(_, key) => failedWorks.contains(key)
      }
    }
}
