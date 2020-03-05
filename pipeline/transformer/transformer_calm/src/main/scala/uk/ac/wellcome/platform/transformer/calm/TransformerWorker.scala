package uk.ac.wellcome.platform.transformer.calm

import akka.{Done, NotUsed}
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.amazonaws.services.sqs.model.Message
import uk.ac.wellcome.bigmessaging.BigMessageSender
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.{Identified, Version}

import scala.concurrent.Future

sealed abstract class CalmWorkerError(msg: String) extends Exception(msg)
case class DecodeKeyError(msg: String) extends CalmWorkerError(msg)
case class StoreReadError(msg: String) extends CalmWorkerError(msg)
case class TransformerError(msg: String) extends CalmWorkerError(msg)
case class MessageSendError(msg: String) extends CalmWorkerError(msg)

/**
  * A transformer worker:
  * - Takes an SQS stream that emits VHS keys
  * - Gets the record of type `In`
  * - Runs it through a transformer and transforms the `In` to `TransformedBaseWork`
  * - Emits the message via `BigMessageSender` to an SNS topic
  */
trait TransformerWorker[In, SenderDest] {
  type StreamMessage = (Message, NotificationMessage)
  type Result[T] = Either[Throwable, T]
  type StoreKey = Version[String, Int]

  def name: String = this.getClass.getSimpleName
  val stream: SQSStream[NotificationMessage]
  val sender: BigMessageSender[SenderDest, TransformedBaseWork]
  val store: VersionedStore[String, Int, In]
  val transformer: Transformer[In]

  val errorSink: Sink[Result[_], Future[Done]] = Sink.ignore

  def withSource(
    source: Source[StreamMessage, NotUsed]): Source[Unit, NotUsed] =
    source
      .via(decodeMessage)
      .via(work)
      .via(done)

  lazy val decodeMessage: Flow[StreamMessage, In, NotUsed] =
    Flow.fromFunction(message => getRecord(decodeKey(message._2)))

  lazy val work: Flow[In, TransformedBaseWork, NotUsed] =
    Flow.fromFunction(sourceData =>
      transformer.transform(sourceData) match {
        case Left(err)     => throw TransformerError(err.toString)
        case Right(result) => result
    })

  lazy val done: Flow[TransformedBaseWork, Unit, NotUsed] =
    Flow.fromFunction(work =>
      sender.sendT(work) toEither match {
        case Left(err) => throw MessageSendError(err.toString)
        case Right(_)  => (): Unit
    })

  private def decodeKey(message: NotificationMessage) =
    fromJson[StoreKey](message.body).toEither match {
      case Left(err)     => throw DecodeKeyError(err.toString)
      case Right(result) => result
    }

  private def getRecord(key: StoreKey) = store.get(key) match {
    case Left(err)                   => throw StoreReadError(err.toString)
    case Right(Identified(_, entry)) => entry
  }

  def run(): Future[Done] = {
    stream.runStream(
      name,
      source => {
        val end = source.via(Flow.fromFunction(message => message._1))
        val processed = withSource(source)

        processed.flatMapConcat(_ => end)
      }
    )
  }
}
