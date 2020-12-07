package uk.ac.wellcome.platform.ingestor.works

import akka.Done
import software.amazon.awssdk.services.sqs.model.Message
import grizzled.slf4j.Logging
import uk.ac.wellcome.bigmessaging.message.BigMessageStream
import uk.ac.wellcome.pipeline_storage.Indexer
import uk.ac.wellcome.platform.ingestor.common.models.IngestorConfig
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}

class WorkIngestorWorkerService[In, Out](
  ingestorConfig: IngestorConfig,
  documentIndexer: Indexer[Out],
  messageStream: BigMessageStream[In],
  transformBeforeIndex: In => Out
)(implicit
  ec: ExecutionContext)
    extends Runnable
    with Logging {

  type FutureBundles = Future[List[Bundle]]
  case class Bundle(message: Message, document: In)

  private val className = this.getClass.getSimpleName

  def run(): Future[Done] =
    for {
      _ <- documentIndexer.init()
      result <- runStream()
    } yield result

  private def runStream(): Future[Done] = {
    messageStream.runStream(
      className,
      _.map { case (msg, document) => Bundle(msg, document) }
        .groupedWithin(
          ingestorConfig.batchSize,
          ingestorConfig.flushInterval
        )
        .mapAsyncUnordered(10) { msgs =>
          for { bundles <- processMessages(msgs.toList) } yield
            bundles.map(_.message)
        }
        .mapConcat(identity)
    )
  }

  private def processMessages(bundles: List[Bundle]): FutureBundles =
    for {
      documents <- Future.successful(bundles.map(m => m.document))
      transformedDocuments = documents.map(transformBeforeIndex)
      either <- documentIndexer.index(documents = transformedDocuments)
    } yield {
      val failedWorks = either.left.getOrElse(Nil)
      bundles.filterNot {
        case Bundle(_, document) => failedWorks.contains(document)
      }
    }
}

object WorkIngestorWorkerService {
  def apply[T](
    ingestorConfig: IngestorConfig,
    documentIndexer: Indexer[T],
    messageStream: BigMessageStream[T])(implicit ec: ExecutionContext) =
    new WorkIngestorWorkerService(
      ingestorConfig,
      documentIndexer,
      messageStream,
      identity[T]
    )
}
