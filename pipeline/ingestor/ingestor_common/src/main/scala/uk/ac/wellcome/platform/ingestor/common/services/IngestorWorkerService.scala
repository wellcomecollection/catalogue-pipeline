package uk.ac.wellcome.platform.ingestor.common.services

import akka.Done
import com.amazonaws.services.sqs.model.Message
import grizzled.slf4j.Logging
import uk.ac.wellcome.bigmessaging.message.BigMessageStream
import uk.ac.wellcome.elasticsearch.ElasticsearchIndexCreator
import uk.ac.wellcome.platform.ingestor.common.Indexer
import uk.ac.wellcome.platform.ingestor.common.models.IngestorConfig
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}

class IngestorWorkerService[T](
                              ingestorConfig: IngestorConfig,
                              indexCreator: ElasticsearchIndexCreator,
  documentIndexer: Indexer[T],
  messageStream: BigMessageStream[T]
)(implicit
  ec: ExecutionContext)
    extends Runnable
    with Logging {

  type FutureBundles = Future[List[Bundle]]
  case class Bundle(message: Message, document: T)

  private val className = this.getClass.getSimpleName

  def run(): Future[Done] =
    for {
      _ <- indexCreator.create
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
      either <- documentIndexer.index(documents = documents)
    } yield {
      val failedWorks = either.left.getOrElse(Nil)
      bundles.filterNot {
        case Bundle(_, document) => failedWorks.contains(document)
      }
    }
}
