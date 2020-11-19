package uk.ac.wellcome.pipeline_storage

import akka.{Done, NotUsed}
import akka.stream.scaladsl.Source
import grizzled.slf4j.Logging
import io.circe.Decoder
import software.amazon.awssdk.services.sqs.model.Message
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sqs.SQSStream

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
case class PipelineStorageConfig(batchSize: Int, flushInterval: FiniteDuration, parallelism: Int)

class PipelineStorageStream[T, D, MsgDestination](messageStream: SQSStream[T], documentIndexer: Indexer[D], messageSender: MessageSender[MsgDestination])(config: PipelineStorageConfig)(implicit ec: ExecutionContext) extends Logging{
  type FutureBundles = Future[List[Bundle]]
  case class Bundle(message: Message, document: D)

  def foreach(streamName: String, process: T => Future[Option[D]])(
    implicit decoderT: Decoder[T]): Future[Done] =
    run(
      streamName = streamName,
      source =>
        source
          .mapAsyncUnordered(parallelism = config.parallelism) {
            case (message, t) =>
              debug(s"Processing message ${message.messageId()}")
              process(t).map(w => (message, w))
          }
    )

  private def run(streamName: String, modifySource: Source[(Message, T), NotUsed] => Source[(Message,Option[D]), NotUsed])(implicit decoder: Decoder[T]) = {

    for {
      _ <- documentIndexer.init()
      result <- messageStream.runStream(
      streamName,
      modifySource(_).map {
        case (msg, Some(document)) => Bundle(msg, document)
        case (msg, None) => ???
      }
        .groupedWithin(
          config.batchSize,
          config.flushInterval
        )
        .mapAsyncUnordered(10) { msgs =>
          for { bundles <- storeDocuments(msgs.toList) } yield
            bundles.map(_.message)
        }
        .mapConcat(identity)
    )
    } yield result
  }

  private def storeDocuments(bundles: List[Bundle]): FutureBundles =
    for {
      either <- documentIndexer.index(documents = bundles.map(m => m.document))
    } yield {
      val failedWorks = either.left.getOrElse(Nil)
      bundles.filterNot {
        case Bundle(_, document) => failedWorks.contains(document)
      }
    }
}
