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
import weco.lambda.STDIODownstream

import scala.concurrent.{ExecutionContext, Future}

object CLIMain extends App {
  implicit val actorSystem: ActorSystem =
    ActorSystem("main-actor-system")
  implicit val ec: ExecutionContext =
    actorSystem.dispatcher

  private val stdinSource: Source[ByteString, Future[IOResult]] =
    StreamConverters.fromInputStream(() => System.in)

  private val lineDelimiter: Flow[ByteString, ByteString, NotUsed] =
    Framing.delimiter(
      ByteString("\n"),
      maximumFrameLength = 256,
      allowTruncation = true
    )
  private val toStringFlow: Flow[ByteString, String, NotUsed] =
    Flow[ByteString].map(_.utf8String)

  private val pathsProcessor = new PathsProcessor(STDIODownstream, 40)
  private val pathsProcessorFlow: Flow[Seq[Path], Future[Seq[Path]], NotUsed] =
    Flow[Seq[Path]].map {
      paths: Seq[Path] => pathsProcessor(paths)
    }

  private val toPathFlow: Flow[String, Path, NotUsed] =
    Flow[String].map(PathFromString)

  stdinSource
    .via(lineDelimiter)
    .via(toStringFlow)
    .via(toPathFlow)
    // this number is pretty arbitrary, but grouping of some kind is needed in order to
    // provide a list to the next step, rather than individual paths
    .grouped(10000)
    .via(pathsProcessorFlow)
    .runWith(Sink.seq)

  actorSystem.terminate()
}
