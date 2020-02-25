package uk.ac.wellcome.platform.transformer.calm

import akka.{Done, NotUsed}
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.amazonaws.services.sqs.model.Message
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.Future

trait Worker[Store, StoreKey, SourceData, SinkData] extends Runnable {
  type Result[T] = Either[Throwable, T]
  type SourceOut = (Message, NotificationMessage)
  val errorSink: Sink[Any, Future[Done]] = Sink.ignore

  def runStream(stream: SQSStream[NotificationMessage]) =
    stream.runStream(streamName = "StreamName", processMessage)

  def processMessage(source: Source[SourceOut, NotUsed]) =
    source.via(getNotificationFlow)

  def divertEither[I, O](flow: Flow[I, Result[O], NotUsed],
                         to: Sink[Any, Future[Done]]): Flow[I, O, NotUsed] =
    flow.divertTo(Sink.ignore, _.isLeft).collect {
      case Right(out) => out
    }

  val getNotificationFlow: Flow[SourceOut, NotificationMessage, NotUsed] =
    Flow.fromFunction(_._2)

  val decodeKeyFlow: Flow[NotificationMessage, StoreKey, NotUsed] =
    divertEither(Flow.fromFunction(decodeKey), to = errorSink)

  val getSourceDataFlow: Flow[StoreKey, SourceData, NotUsed] =
    divertEither(Flow.fromFunction(getSourceData), to = errorSink)

  val workFlow: Flow[SourceData, SinkData, NotUsed] =
    divertEither(Flow.fromFunction(work), to = errorSink)

  val doneFlow: Flow[SinkData, Unit, NotUsed] =
    divertEither(Flow.fromFunction(done), to = errorSink)

  def decodeKey(notification: NotificationMessage): Result[StoreKey] =
    fromJson[StoreKey](notification.body) toEither match {
      case Left(err)  => Left(err)
      case Right(key) => Right(key)
    }

  def getSourceData(key: StoreKey): Result[SourceData]

  def work(sourceData: SourceData): Result[SinkData]

  def done(sinkData: SinkData): Result[Unit]
}
