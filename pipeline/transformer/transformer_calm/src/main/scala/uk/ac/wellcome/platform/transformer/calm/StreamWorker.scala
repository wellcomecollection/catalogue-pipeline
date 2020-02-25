package uk.ac.wellcome.platform.transformer.calm

import akka.{Done, NotUsed}
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.Future

trait StreamWorker[Message, MessageData, SinkData] {
  private type Result[T] = Either[Throwable, T]
  def errorSink: Sink[Result[_], Future[Done]] = Sink.ignore

  def processMessage(source: Source[Message, NotUsed]) =
    source.via(decodeMessageDiverted).via(workDiverted).via(doneDiverted)

  private def divertEither[I, O](
    flow: Flow[I, Result[O], NotUsed],
    to: Sink[Result[_], Future[Done]]): Flow[I, O, NotUsed] =
    flow.divertTo(Sink.ignore, _.isLeft).collect {
      case Right(out) => out
    }

  val decodeMessageFlow: Flow[Message, Result[MessageData], NotUsed]
  val workFlow: Flow[MessageData, Result[SinkData], NotUsed]
  val doneFlow: Flow[SinkData, Result[Unit], NotUsed]

  val decodeMessageDiverted: Flow[Message, MessageData, NotUsed] =
    divertEither(decodeMessageFlow, to = errorSink)

  val workDiverted: Flow[MessageData, SinkData, NotUsed] =
    divertEither(workFlow, to = errorSink)

  val doneDiverted: Flow[SinkData, Unit, NotUsed] =
    divertEither(doneFlow, to = errorSink)
}
