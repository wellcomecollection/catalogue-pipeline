package uk.ac.wellcome.platform.transformer.calm

import akka.{Done, NotUsed}
import akka.stream.scaladsl.{Flow, Sink, Source}
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.Future

trait Worker[Message, MessageData, SinkData] {
  private type Result[T] = Either[Throwable, T]
  def errorSink: Sink[Result[_], Future[Done]] = Sink.ignore

  def processMessage(source: Source[Message, NotUsed]) =
    source.via(decodeMessageFlow).via(workFlow).via(doneFlow)

  private def divertEither[I, O](
    flow: Flow[I, Result[O], NotUsed],
    to: Sink[Result[_], Future[Done]]): Flow[I, O, NotUsed] =
    flow.divertTo(Sink.ignore, _.isLeft).collect {
      case Right(out) => out
    }

  val decodeMessageFlow: Flow[Message, MessageData, NotUsed] =
    divertEither(Flow.fromFunction(decodeMessage), to = errorSink)

  val workFlow: Flow[MessageData, SinkData, NotUsed] =
    divertEither(Flow.fromFunction(work), to = errorSink)

  val doneFlow: Flow[SinkData, Unit, NotUsed] =
    divertEither(Flow.fromFunction(done), to = errorSink)

  def decodeMessage(message: Message): Result[MessageData]
  def work(messageData: MessageData): Result[SinkData]
  def done(sinkData: SinkData): Result[Unit]
}
