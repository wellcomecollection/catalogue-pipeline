package weco.tei.adapter

import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.storage.store.VersionedStore
import weco.catalogue.source_model.TeiSourcePayload
import weco.catalogue.source_model.tei.{
  TeiChangedMetadata,
  TeiDeletedMetadata,
  TeiIdChangeMessage,
  TeiIdDeletedMessage,
  TeiIdMessage,
  TeiMetadata
}

import scala.concurrent.ExecutionContext

class TeiAdapterWorkerService[Dest](
  messageStream: SQSStream[NotificationMessage],
  messageSender: MessageSender[Dest],
  store: VersionedStore[String, Int, TeiMetadata]
)(implicit val ec: ExecutionContext)
    extends Runnable {
  val className = this.getClass.getSimpleName

  override def run(): Unit =
    messageStream.runStream(
      className,
      source =>
        source.map {
          case (msg, notificationMessage) =>
            val tried = for {
              idMessage <- fromJson[TeiIdMessage](
                notificationMessage.body
              )
              metadata = toMetadata(idMessage)

              stored <- store
                .upsert(idMessage.id)(metadata)(
                  existingMetadata =>
                    metadata match {
                      case TeiChangedMetadata(_, time) =>
                        if (existingMetadata.time.isAfter(time)) {
                          Right(existingMetadata)
                        } else {
                          Right(metadata)
                        }
                      case TeiDeletedMetadata(time) =>
                        if (time.isAfter(existingMetadata.time)) {
                          Right(metadata)
                        } else {
                          Right(existingMetadata)
                        }
                    }
                )
                .left
                .map(_.e)
                .toTry
              _ <- messageSender.sendT(
                TeiSourcePayload(
                  stored.id.id,
                  stored.identifiedT,
                  stored.id.version
                )
              )
            } yield msg

            tried.get
        }
    )

  private def toMetadata(message: TeiIdMessage) = message match {
    case TeiIdChangeMessage(_, s3Location, timeModified) =>
      TeiChangedMetadata(
        s3Location,
        timeModified
      )
    case TeiIdDeletedMessage(_, timeDeleted) =>
      TeiDeletedMetadata(
        timeDeleted
      )
  }
}
