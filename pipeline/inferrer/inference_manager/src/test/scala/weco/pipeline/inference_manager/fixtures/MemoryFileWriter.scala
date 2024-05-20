package weco.pipeline.inference_manager.fixtures

import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import akka.Done
import akka.stream.IOResult
import akka.stream.scaladsl.{Flow, Keep, Sink}
import akka.util.ByteString
import grizzled.slf4j.Logging
import weco.pipeline.inference_manager.services.FileWriter

import scala.concurrent.Future
import scala.collection.JavaConverters._
import scala.collection._

class MemoryFileWriter extends FileWriter with Logging {
  val files: concurrent.Map[Path, ByteString] =
    new ConcurrentHashMap[Path, ByteString].asScala

  def write(path: Path): Sink[ByteString, Future[IOResult]] =
    Flow[ByteString]
      .map(files.put(path, _))
      .toMat(Sink.fold(IOResult(0L))({
        (result, _) =>
          IOResult(result.count + 1)
      }))(Keep.right)

  def delete: Sink[Path, Future[Done]] =
    Sink.foreach[Path](files.remove(_))
}
