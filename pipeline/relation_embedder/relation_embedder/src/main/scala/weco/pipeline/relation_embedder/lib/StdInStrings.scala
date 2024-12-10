package weco.pipeline.relation_embedder.lib

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.IOResult
import org.apache.pekko.stream.scaladsl.{
  Flow,
  Framing,
  Source,
  StreamConverters
}
import org.apache.pekko.util.ByteString

import scala.concurrent.Future

trait StdInStrings {
  private val stdinSource: Source[ByteString, Future[IOResult]] =
    StreamConverters.fromInputStream(() => System.in)

  private val lineDelimiter: Flow[ByteString, ByteString, NotUsed] =
    Framing.delimiter(
      ByteString("\n"),
      maximumFrameLength = 256,
      allowTruncation = true
    )

  private val toStringFlow: Flow[ByteString, String, NotUsed] = {
    Flow[ByteString].map(_.utf8String)
  }

  protected val stringSource: Source[String, Future[IOResult]] = stdinSource
    .via(lineDelimiter)
    .via(toStringFlow)

}
