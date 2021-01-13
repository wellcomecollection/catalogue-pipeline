package uk.ac.wellcome.pipeline_storage

import akka.stream.FlowShape
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge}
import akka.{Done, NotUsed}
import grizzled.slf4j.Logging
import io.circe.Decoder
import software.amazon.awssdk.services.sqs.model.Message
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sqs.SQSStream

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, TimeoutException}

case class PipelineStorageConfig(batchSize: Int,
                                 flushInterval: FiniteDuration,
                                 parallelism: Int)

case class Bundle[T](message: Message, item: T, numberOfItems: Int)

class PipelineStorageStream[In, Out, MsgDestination](
  messageStream: SQSStream[In],
  indexer: Indexer[Out],
  messageSender: MessageSender[MsgDestination])(config: PipelineStorageConfig)(
  implicit ec: ExecutionContext)
    extends Logging {

  import PipelineStorageStream._

  def foreach(streamName: String, process: In => Future[List[Out]])(
    implicit decoderT: Decoder[In],
    indexable: Indexable[Out]): Future[Done] =
    run(
      streamName = streamName,
      Flow[(Message, In)]
        .mapAsyncUnordered(parallelism = config.parallelism) {
          case (message, in) =>
            debug(s"Processing message ${message.messageId()}")
            process(in).map(w => (message, w))
        }
    )

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
                batchAndSendFlow(config, messageSender, indexer),
                identityFlow)
          )
      )
    } yield done

  private val identityFlow: Flow[(Message, List[Out]), Message, NotUsed] =
    Flow[(Message, List[Out])]
      .collect { case (message, Nil) => message }

  private def broadcastAndMerge[I, O](
    a: Flow[I, O, NotUsed],
    b: Flow[I, O, NotUsed]): Flow[I, O, NotUsed] =
    Flow.fromGraph(
      GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._
        val broadcast = builder.add(Broadcast[I](2))
        val merge = builder.add(Merge[O](2))
        broadcast ~> a ~> merge
        broadcast ~> b ~> merge
        FlowShape(broadcast.in, merge.out)
      }
    )
}

object PipelineStorageStream extends Logging {

  def batchRetrieveFlow[T](config: PipelineStorageConfig,
                           retriever: Retriever[T])(
    implicit ec: ExecutionContext): Flow[Bundle[String], Bundle[T], NotUsed] =
    Flow[Bundle[String]]
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
              .collect { case Some((msg, doc)) => Bundle(msg, doc, 1) }
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
          if (failed.nonEmpty) warn(s"Some documents failed ingesting: $failed")
          bundles.collect {
            case Bundle(message, doc, numberOfItems) if !failed.contains(doc) =>
              Bundle(message, doc, numberOfItems)
          }
        }
      }
      .mapConcat(identity)

  def batchAndSendFlow[T, MsgDestination](
    config: PipelineStorageConfig,
    msgSender: MessageSender[MsgDestination],
    indexer: Indexer[T])(implicit
                         ec: ExecutionContext,
                         indexable: Indexable[T]) = {
    val maxSubStreams = Integer.MAX_VALUE
    Flow[(Message, List[T])]
      .collect {
        case (msg, items@_ :: _) =>
          items.map(item =>
            Bundle[T](message = msg, item = item, numberOfItems = items.size))
      }
      .mapConcat[Bundle[T]](identity)
      .via(batchIndexFlow(config, indexer))
      .via(groupByMessage(maxSubStreams, 5 minutes).mergeSubstreams)
      .mapConcat(identity)
      .mapAsyncUnordered(config.parallelism) { bundle =>
        for {
          _ <- Future.fromTry(msgSender.send(indexable.id(bundle.item)))
        } yield bundle
      }
      .via(groupByMessage[T](maxSubStreams, 5 minutes)
        .collect {
          case head :: _ => head.message
        }
        .mergeSubstreams)
  }

  // Splits the flow into a substream for each messageId.
  // Each substream emits one message with the complete list of bundles for the same messageId
  // or no message if it didn't receive the correct number of bundles
  def groupByMessage[T](maxSubStreams: Int, t: FiniteDuration) =
    Flow[Bundle[T]]
      .groupBy(maxSubstreams = maxSubStreams, f = _.message.messageId(), allowClosedSubstreamRecreation = true)
      .scan(Nil: List[Bundle[T]]) {
        case (bundleList, b) => b :: bundleList
      }
      .filter { list =>
        list.nonEmpty && list
          .map(_.item)
          .distinct
          .size == list.head.numberOfItems
      }
    // Close the substream with take if it succeeds
    // or after timeout if it fails
      .take(1)
      .initialTimeout(t).recover{
      case e: TimeoutException =>
        warn("Timeout when processing substream",e)
        Nil
    }

  private def unzipBundles[T](
    bundles: Seq[Bundle[T]]): (List[Message], List[T]) =
    bundles.toList
      .unzip(bundle => bundle.message -> bundle.item)
}
