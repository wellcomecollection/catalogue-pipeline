package weco.tei.adapter

import akka.{Done, NotUsed}
import akka.stream.scaladsl.{Flow, Source}
import software.amazon.awssdk.services.sqs.model.Message
import weco.json.JsonUtil.fromJson
import weco.messaging.MessageSender
import weco.messaging.sns.NotificationMessage
import weco.messaging.sqs.SQSStream
import weco.storage.store.VersionedStore
import weco.catalogue.source_model.TeiSourcePayload
import weco.catalogue.source_model.tei.{
  TeiChangedMetadata,
  TeiDeletedMetadata,
  TeiIdChangeMessage,
  TeiIdDeletedMessage,
  TeiIdMessage,
  TeiMetadata
}
import weco.catalogue.source_model.Implicits._
import weco.flows.FlowOps
import weco.typesafe.Runnable

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class TeiAdapterWorkerService[Dest](
  messageStream: SQSStream[NotificationMessage],
  messageSender: MessageSender[Dest],
  store: VersionedStore[String, Int, TeiMetadata],
  parallelism: Int,
  delay: FiniteDuration
)(implicit val ec: ExecutionContext)
    extends Runnable
    with FlowOps {
  val className = this.getClass.getSimpleName

  override def run(): Future[Done] =
    messageStream.runStream(
      className,
      runStream
    )

  private def runStream(
    source: Source[(Message, NotificationMessage), NotUsed]
  ) =
    source
      .via(unwrapMessage)
      .via(broadcastAndMerge(delayDeleted, sendChanged))
      .mapAsync(parallelism) { case (ctx, msg) =>
        Future.fromTry {
          for {
            stored <- updateStore(msg.id, msg.toMetadata)
            _ <- messageSender.sendT(
              TeiSourcePayload(
                stored.id.id,
                stored.identifiedT,
                stored.id.version
              )
            )

          } yield ctx.msg
        }

      }

  def unwrapMessage =
    Flow[(Message, NotificationMessage)]
      .map { case (msg, NotificationMessage(body)) =>
        (Context(msg), fromJson[TeiIdMessage](body).toEither)
      }
      .via(catchErrors)

  // If a file is moved in the tei repo, the tei updater lambda sends
  // to the tei extractor a delete message for the old path and a
  // change message for the new path. The tei extractor delays delete
  // messages to recognise the case of a file moved and avoid sending
  // a delete message for an id that as been moved. However, if the delay
  // isn't large enough some delete message could "escape" and be
  // propagated incorrectly to the tei adapter. So we delay them here as well
  def delayDeleted =
    Flow[(Context, TeiIdMessage)]
      .collect {
        case (ctx, msg) if msg.isInstanceOf[TeiIdDeletedMessage] =>
          (ctx, msg.asInstanceOf[TeiIdDeletedMessage])
      }
      .delay(delay)

  def sendChanged = Flow[(Context, TeiIdMessage)].collect {
    case (ctx, msg) if msg.isInstanceOf[TeiIdChangeMessage] =>
      (ctx, msg.asInstanceOf[TeiIdChangeMessage])
  }

  private def updateStore(id: String, metadata: TeiMetadata) =
    store
      .upsert(id)(metadata)(existingMetadata =>
        updateMetadata(
          newMetadata = metadata,
          existingMetadata = existingMetadata
        )
      )
      .left
      .map(_.e)
      .toTry

  private def updateMetadata(
    newMetadata: TeiMetadata,
    existingMetadata: TeiMetadata
  ) = {
    newMetadata match {
      case TeiChangedMetadata(_, changedTime) =>
        if (existingMetadata.time.isAfter(changedTime)) {
          Right(existingMetadata)
        } else {
          Right(newMetadata)
        }
      case TeiDeletedMetadata(deletedTime) =>
        if (deletedTime.isAfter(existingMetadata.time)) {
          Right(newMetadata)
        } else {
          Right(existingMetadata)
        }
    }
  }

  /** Encapsulates context to pass along each akka-stream stage. Newer versions
    * of akka-streams have the asSourceWithContext/ asFlowWithContext idioms for
    * this purpose, which we can migrate to if the library is updated.
    */
  case class Context(msg: Message)
}
