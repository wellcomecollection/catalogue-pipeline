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
case class PipelineStorageConfig(batchSize: Int, flushInterval: FiniteDuration, parallelism: Int)

class PipelineStorageStream[T, D, MsgDestination](messageStream: SQSStream[T], documentIndexer: Indexer[D], messageSender: MessageSender[MsgDestination])(config: PipelineStorageConfig)(implicit ec: ExecutionContext) extends Logging{
  type FutureBundles = Future[List[Bundle]]
  case class Bundle(message: Message, document: D)

  def foreach(streamName: String, process: T => Future[Option[D]])(
    implicit decoderT: Decoder[T], indexable: Indexable[D]): Future[Done] =
    run(
      streamName = streamName,
      Flow[(Message,T)]
          .mapAsyncUnordered(parallelism = config.parallelism) {
            case (message, t) =>
              debug(s"Processing message ${message.messageId()}")
              process(t).map(w => (message, w))
          }
    )

  def run(streamName: String, processFlow: Flow[(Message, T), (Message,Option[D]), NotUsed])(implicit decoder: Decoder[T], indexable: Indexable[D]) = {
  val identityFlow = Flow[(Message, Option[D])].collect {case (message, None) => message}
    for {
      _ <- documentIndexer.init()
      result <- messageStream.runStream(
      streamName,
        _.via(processFlow).via(broadcastAndMerge(batchAndSendFlow, identityFlow)))
    } yield result
  }

  private def batchAndSendFlow(implicit indexable: Indexable[D]) = {
    Flow[(Message, Option[D])].collect { case (message, Some(document)) => Bundle(message, document) }.groupedWithin(
      config.batchSize,
      config.flushInterval
    )
      .mapAsyncUnordered(10) { msgs =>
        storeDocuments(msgs.toList)
      }
      .mapConcat(identity).mapAsyncUnordered(config.parallelism) { bundle =>
      for {
        _ <- Future.fromTry(messageSender.send(indexable.id(bundle.document)))
      } yield bundle.message
    }
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

  def broadcastAndMerge[I, O](a: Flow[I, O, NotUsed], b: Flow[I, O, NotUsed]): Flow[I, O, NotUsed] =
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
