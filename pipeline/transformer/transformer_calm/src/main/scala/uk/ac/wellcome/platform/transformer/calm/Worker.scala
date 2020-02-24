package uk.ac.wellcome.platform.transformer.calm

import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.platform.transformer.calm.service.{CalmTransformerWorkerServiceState, MessageReadError}
import uk.ac.wellcome.storage.Version

trait Worker[Store, StoreKey, SourceData, SinkData] extends Runnable {
  type Result[T] = Either[Throwable, T]

  def runStream(stream: SQSStream[NotificationMessage]) = stream.foreach(this.getClass.getSimpleName, processMessage)

  def processMessage(message: NotificationMessage) = for {
    key <- readMessage(message)
    storeRecord <- get(key)
    sinkData <- doWork(storeRecord)
    sent <- done(sinkData)
  } yield sent

  def get(storeKey: StoreKey): Result[SourceData]

  private def readMessage(message: NotificationMessage): Result[StoreKey] =
    fromJson[StoreKey](message.body).toEither match {
      case Left(err) => Left(new Exception(s"Worker: Cannot read message, ${err}"))
      case _         => _
    }

  def doWork(sourceData: SourceData): Result[SinkData]

  def done(sinkData: SinkData): Result[Unit]
}
