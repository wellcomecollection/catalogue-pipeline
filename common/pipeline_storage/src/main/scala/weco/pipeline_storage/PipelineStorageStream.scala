package weco.pipeline_storage

import akka.stream.scaladsl.Flow
import akka.{Done, NotUsed}
import grizzled.slf4j.Logging
import io.circe.Decoder
import software.amazon.awssdk.services.sqs.model.Message
import weco.messaging.{MessageSender, MessageSenderError}
import weco.messaging.sns.NotificationMessage
import weco.messaging.sqs.SQSStream
import weco.flows.FlowOps

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, TimeoutException}

case class PipelineStorageConfig(batchSize: Int,
                                 flushInterval: FiniteDuration,
                                 parallelism: Int)

case class Bundle[T](message: Message, item: T, numberOfItems: Int)

class PipelineStorageStream[In, Out, MsgDestination](
  messageStream: SQSStream[In],
  indexer: Indexer[Out],
  messageSender: MessageSender[MsgDestination])(
  val config: PipelineStorageConfig)(implicit val ec: ExecutionContext)
    extends Logging
    with FlowOps {

  import PipelineStorageStream._

  def foreach(streamName: String, process: In => Future[List[Out]])(
    implicit decoderT: Decoder[In],
    indexable: Indexable[Out]): Future[Done] =
    run(streamName = streamName, processFlow(config, process))

  def run(streamName: String,
          processFlow: Flow[(Message, In), (Message, List[Out]), NotUsed])(
    implicit decoder: Decoder[In],
    indexable: Indexable[Out]): Future[Done] =
    for {
      _ <- indexer.init()
      done: Done <- messageStream.runStream(
        streamName,
        source =>
          source
            .via(processFlow)
            .via(
              broadcastAndMerge(
                batchIndexAndSendFlow(
                  config,
                  (item: Out) =>
                    sendIndexable[Out, MsgDestination](messageSender)(item),
                  indexer
                ),
                noOutputFlow)
          )
      )
    } yield done

}

object PipelineStorageStream extends Logging {
  def batchIndexAndSendFlow[T, MsgDestination](config: PipelineStorageConfig,
                                               send: T => Either[MessageSenderError, Unit],
                                               indexer: Indexer[T])(
    implicit
    ec: ExecutionContext,
    indexable: Indexable[T]) = {
    val maxSubStreams = Integer.MAX_VALUE
    Flow[(Message, List[T])]
      .collect {
        case (msg, items @ _ :: _) =>
          items.map(item =>
            Bundle[T](message = msg, item = item, numberOfItems = items.size))
      }
      .mapConcat[Bundle[T]](identity)
      .via(batchIndexFlow(config, indexer))
      .via(takeListsOfCompleteBundles(maxSubStreams, 5 minutes))
      .mapConcat(identity)
      .mapAsyncUnordered(config.parallelism) { bundle =>
        for {
          _ <- send(bundle.item) match {
            case Right(_)  => Future.successful(())
            case Left(err) => Future.failed(err.e)
          }
        } yield bundle
      }
      .via(takeListsOfCompleteBundles[T](maxSubStreams, 5 minutes)
        .collect {
          case head :: _ => head.message
        })
  }

  def processFlow[In, Out](
    config: PipelineStorageConfig,
    process: In => Future[List[Out]])(implicit ec: ExecutionContext)
    : Flow[(Message, In), (Message, List[Out]), NotUsed] =
    Flow[(Message, In)].mapAsyncUnordered(parallelism = config.parallelism) {
      case (message, in) =>
        debug(s"Processing message ${message.messageId()}")
        process(in).map(w => (message, w))
    }

  def batchRetrieveFlow[T](
    config: PipelineStorageConfig,
    retriever: Retriever[T])(implicit ec: ExecutionContext)
    : Flow[(Message, NotificationMessage), (Message, T), NotUsed] =
    Flow[(Message, NotificationMessage)]
      .map {
        case (message, notificationMessage) =>
          Bundle(message, notificationMessage.body, numberOfItems = 1)
      }
      .groupedWithin(config.batchSize, config.flushInterval)
      .mapAsyncUnordered(parallelism = config.parallelism) { bundles =>
        val (messages, ids) = unzipBundles(bundles)
        retriever(ids)
          .map { result =>
            ids.zipWithIndex
              .map {
                case (id, idx) =>
                  result(id) match {
                    case Left(err) =>
                      error(s"Could not retrieve document with id: $id", err)
                      None
                    case Right(doc) => Some((messages(idx), doc))
                  }
              }
              .collect { case Some((msg, doc)) => (msg, doc) }
          }
      }
      .mapConcat(identity)

  def batchIndexFlow[T](config: PipelineStorageConfig, indexer: Indexer[T])(
    implicit
    ec: ExecutionContext,
    indexable: Indexable[T]): Flow[Bundle[T], Bundle[T], NotUsed] =
    Flow[Bundle[T]]
      .groupedWeightedWithin(
        config.batchSize,
        config.flushInterval
      ) {
        case Bundle(_, item, _) => indexable.weight(item)
      }
      .mapAsyncUnordered(config.parallelism) { bundles =>
        val (_, items) = unzipBundles(bundles)
        indexer(items).map { result =>
          val failed = result.left.getOrElse(Nil)
          if (failed.nonEmpty) {
            val failedIds = failed.map { indexable.id }
            warn(
              s"Some documents failed ingesting: ${failedIds.mkString(", ")}")
          }
          bundles.collect {
            case Bundle(message, doc, numberOfItems) if !failed.contains(doc) =>
              Bundle(message, doc, numberOfItems)
          }
        }
      }
      .mapConcat(identity)

  // Splits the flow into a subsflow for each messageId.
  // Each substream emits one message with the complete list of bundles for the same messageId
  // or no message if it didn't receive the correct number of bundles
  // The result is a flow of list of _complete_ bundles
  def takeListsOfCompleteBundles[T](
    maxSubStreams: Int,
    t: FiniteDuration): Flow[Bundle[T], List[Bundle[T]], NotUsed] = {
    val groupByMessage = Flow[Bundle[T]]
      .groupBy(
        maxSubStreams,
        m => (m.message.messageId(), m.message.md5OfMessageAttributes()),
        allowClosedSubstreamRecreation = true)
      .scan(Nil: List[Bundle[T]]) {
        case (bundleList, b) => b :: bundleList
      }
    groupByMessage
      .filter { list =>
        list.nonEmpty && list
          .map(_.item)
          .distinct
          .size == list.head.numberOfItems
      }
      // There's a maximum number of substreams that you can have,
      // so we need to close them or eventually we will run out of substreams.
      // Because they always emit one or no message, close the substream with take if it succeeds
      // or after timeout if it fails
      .take(1)
      .initialTimeout(t)
      .recover {
        case e: TimeoutException =>
          warn("Timeout when processing substream", e)
          Nil
      }
      .mergeSubstreams
  }

  def noOutputFlow[Out]: Flow[(Message, List[Out]), Message, NotUsed] =
    Flow[(Message, List[Out])]
      .collect { case (message, Nil) => message }

  private def unzipBundles[T](
    bundles: Seq[Bundle[T]]): (List[Message], List[T]) =
    bundles.toList
      .unzip(bundle => bundle.message -> bundle.item)

  private def sendIndexable[T, Destination](
    messageSender: MessageSender[Destination])(item: T)(
    implicit indexable: Indexable[T]) =
    messageSender.send(indexable.id(item))
}
