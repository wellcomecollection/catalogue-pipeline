package weco.pipeline.batcher
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.IOResult
import org.apache.pekko.stream.scaladsl.{
  Flow,
  Framing,
  Sink,
  Source,
  StreamConverters
}
import org.apache.pekko.util.ByteString

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object CLIMain extends App {
  implicit val actorSystem: ActorSystem =
    ActorSystem("main-actor-system")
  implicit val ec: ExecutionContext =
    actorSystem.dispatcher

  println("hello")
  val stdinSource: Source[ByteString, Future[IOResult]] =
    StreamConverters.fromInputStream(() => System.in)

  val lineDelimiter: Flow[ByteString, ByteString, NotUsed] =
    Framing.delimiter(
      ByteString("\n"),
      maximumFrameLength = 256,
      allowTruncation = true
    )
  val toStringFlow: Flow[ByteString, String, NotUsed] =
    Flow[ByteString].map(_.utf8String)

  val pathsProcessorFlow: Flow[Seq[String], Future[Seq[Long]], NotUsed] =
    Flow[Seq[String]].map {
      paths: Seq[String] =>
        PathsProcessor(
          40, // TODO: 40 is the number in the config used by Main, do this properly later
          paths.toList,
          STDIODownstream
        )
    }

  stdinSource
    .via(lineDelimiter)
    .via(toStringFlow)
    // this number is pretty arbitrary, but grouping of some kind is needed in order to
    // provide a list to the next step, rather than individual paths
    .grouped(10000)
    .via(pathsProcessorFlow)
    .runWith(Sink.seq)
  actorSystem.terminate()
  private object STDIODownstream extends Downstream {
    override def notify(batch: Batch): Try[Unit] = Try(println(batch))
  }
}
