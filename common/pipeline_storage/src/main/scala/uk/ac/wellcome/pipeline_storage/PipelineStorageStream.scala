package uk.ac.wellcome.pipeline_storage

import akka.stream.FlowShape
import akka.{Done, NotUsed}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge}
import grizzled.slf4j.Logging
import io.circe.Decoder
import software.amazon.awssdk.services.sqs.model.Message
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sqs.SQSStream

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

case class PipelineStorageConfig(batchSize: Int,
                                 flushInterval: FiniteDuration,
                                 parallelism: Int)

case class Bundle[T](message: Message, document: T)

class PipelineStorageStream[In, Out, MsgDestination](
  messageStream: SQSStream[In],
  documentIndexer: Indexer[Out],
  messageSender: MessageSender[MsgDestination])(config: PipelineStorageConfig)(
  implicit ec: ExecutionContext)
    extends Logging {

  def foreach(streamName: String, process: In => Future[Option[Out]])(
    implicit decoderT: Decoder[In],
    indexable: Indexable[Out]): Future[Done] =
    run(
      streamName = streamName,
      Flow[(Message, In)]
        .mapAsyncUnordered(parallelism = config.parallelism) {
          case (message, t) =>
            debug(s"Processing message ${message.messageId()}")
            process(t).map(w => (message, w))
        }
    )

  def run(streamName: String,
          processFlow: Flow[(Message, In), (Message, Option[Out]), NotUsed])(
    implicit decoder: Decoder[In],
    indexable: Indexable[Out]): Future[Done] = {
    val identityFlow = Flow[(Message, Option[Out])].collect {
      case (message, None) => message
    }
    for {
      _ <- documentIndexer.init()
      result <- messageStream.runStream(
        streamName,
        _.via(processFlow)
          .via(broadcastAndMerge(batchAndSendFlow, identityFlow)))
    } yield result
  }

  private def batchAndSendFlow(implicit indexable: Indexable[Out]) =
    Flow[(Message, Option[Out])]
      .collect { case (message, Some(document)) => Bundle(message, document) }
      .groupedWeightedWithin(
        config.batchSize,
        config.flushInterval
      )(bundle => indexable.weight(bundle.document))
      .mapAsyncUnordered(config.parallelism) { msgs =>
        storeDocuments(msgs.toList)
      }
      .mapConcat(identity)
      .mapAsyncUnordered(config.parallelism) { bundle =>
        for {
          _ <- Future.fromTry(messageSender.send(indexable.id(bundle.document)))
        } yield bundle.message
      }

  private def storeDocuments(
    bundles: List[Bundle[Out]]): Future[List[Bundle[Out]]] =
    for {
      either <- documentIndexer.index(documents = bundles.map(m => m.document))
    } yield {
      val failedWorks = either.left.getOrElse(Nil)
      bundles.filterNot {
        case Bundle(_, document) => failedWorks.contains(document)
      }
    }

  def broadcastAndMerge[I, O](a: Flow[I, O, NotUsed],
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
