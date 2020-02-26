package uk.ac.wellcome.platform.transformer.calm

import akka.{Done, NotUsed}
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.Future

trait StreamWorker[Message, MessageData, SinkData] {
  private type Result[T] = Either[Throwable, T]
  val errorSink: Sink[Result[_], Future[Done]] = Sink.ignore

  def withSource(source: Source[Message, NotUsed]): Source[Unit, NotUsed] =
    source.via(decodeMessageDiverted).via(workDiverted).via(doneDiverted)

  private def divertEither[I, O](
    flow: Flow[I, Result[O], NotUsed],
    to: Sink[Result[_], Future[Done]]): Flow[I, O, NotUsed] =
    flow.divertTo(errorSink, _.isLeft).collect {
      case Right(out) => out
    }

  val decodeMessage: Flow[Message, Result[MessageData], NotUsed]
  val work: Flow[MessageData, Result[SinkData], NotUsed]
  val done: Flow[SinkData, Result[Unit], NotUsed]

  val decodeMessageDiverted: Flow[Message, MessageData, NotUsed] =
    divertEither(decodeMessage, to = errorSink)

  val workDiverted: Flow[MessageData, SinkData, NotUsed] =
    divertEither(work, to = errorSink)

  val doneDiverted: Flow[SinkData, Unit, NotUsed] =
    divertEither(done, to = errorSink)
}
