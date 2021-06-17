package weco.tei.adapter

import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import weco.catalogue.tei.models.{
  TeiIdChangeMessage,
  TeiMetadata,
  TeiStoreRecord
}

import scala.concurrent.ExecutionContext

class TeiAdapterWorkerService[Dest](
  messageStream: SQSStream[NotificationMessage],
  messageSender: MessageSender[Dest]
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
              _ <- messageSender.sendT(
                TeiStoreRecord(
                  changeMessage.id,
                  TeiMetadata(
                    false,
                    changeMessage.s3Location,
                    changeMessage.timeModified
                  ),
                  1
                )
              )
            } yield msg

            tried.get
        }
    )
}
