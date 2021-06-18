package weco.tei.adapter

import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.storage.store.VersionedStore
import weco.catalogue.source_model.TeiSourcePayload
import weco.catalogue.source_model.tei.{TeiIdChangeMessage, TeiMetadata}

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
              changeMessage <- fromJson[TeiIdChangeMessage](
                notificationMessage.body
              )
              metadata = TeiMetadata(
                false,
                changeMessage.s3Location,
                changeMessage.timeModified
              )
              stored <- store.upsert(changeMessage.id)(metadata)(existingMetadata =>
                if(existingMetadata.timeModified.isAfter(metadata.timeModified)) {
                  Right(existingMetadata)
              }else {
                  Right(metadata)
                }
              ).left.map(_.e).toTry
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
}
