package uk.ac.wellcome.platform.inference_manager.fixtures

import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

import akka.Done
import akka.stream.{IOResult, Materializer}
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import uk.ac.wellcome.platform.inference_manager.services.FileWriter

import scala.concurrent.Future
import scala.collection.JavaConverters._
import scala.collection._

class MemoryFileWriter extends FileWriter {
  val files: concurrent.Map[Path, ByteString] =
    new ConcurrentHashMap[Path, ByteString].asScala

  def write(implicit materializer: Materializer)
    : Sink[(ByteString, Path), Future[IOResult]] =
    Sink
      .foreach[(ByteString, Path)] {
        case (file, path) => files.put(path, file)
      }
      .mapMaterializedValue(_ => Future.successful(IOResult(1)))

  def delete(implicit materializer: Materializer): Sink[Path, Future[Done]] =
    Sink.foreach[Path](files.remove(_))
}
