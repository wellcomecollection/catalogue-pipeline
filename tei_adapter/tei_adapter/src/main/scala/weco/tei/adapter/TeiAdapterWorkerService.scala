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
  TeiIdMessage,
  TeiMetadata
}

import scala.concurrent.{ExecutionContext, Future}

class TeiAdapterWorkerService[Dest](
  messageStream: SQSStream[NotificationMessage],
  messageSender: MessageSender[Dest],
  store: VersionedStore[String, Int, TeiMetadata],
  parallelism: Int
)(implicit val ec: ExecutionContext)
    extends Runnable {
  val className = this.getClass.getSimpleName

  override def run(): Unit =
    messageStream.runStream(
      className,
      source =>
        source.mapAsync(parallelism) {
          case (msg, notificationMessage) =>
            Future.fromTry({
              for {
                idMessage <- fromJson[TeiIdMessage](
                  notificationMessage.body
                )
                metadata = idMessage.toMetadata

                stored <- updateStore(idMessage.id, metadata)
                _ <- messageSender.sendT(
                  TeiSourcePayload(
                    stored.id.id,
                    stored.identifiedT,
                    stored.id.version
                  )
                )

              } yield msg
            })

        }
    )

  private def updateStore(id: String, metadata: TeiMetadata) = store
      .upsert(id)(metadata)(
        existingMetadata =>
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

}
