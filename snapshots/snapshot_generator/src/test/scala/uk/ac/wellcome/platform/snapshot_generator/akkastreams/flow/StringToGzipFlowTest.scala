package uk.ac.wellcome.platform.snapshot_generator.akkastreams.flow

import java.io.File
import java.nio.file.Paths

import akka.stream.scaladsl.{FileIO, Flow, Keep, Sink, Source}
import akka.stream.IOResult
import akka.util.ByteString
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.platform.snapshot_generator.test.utils.GzipUtils

import scala.concurrent.Future

class StringToGzipFlowTest
    extends AnyFunSpec
    with Matchers
    with Akka
    with GzipUtils
    with ScalaFutures
    with IntegrationPatience {

  it("produces a gzip-compressed file from the lines") {
    withActorSystem { implicit actorSystem =>
      val flow = StringToGzipFlow()

      val expectedLines = List(
        "Amber avocados age admirably",
        "Bronze bananas barely bounce",
        "Cerise coconuts craftily curl"
      )

      val source = Source(expectedLines)

      // This code dumps the gzip contents to a gzip file.
      val tmpfile = File.createTempFile("stringToGzipFlowTest", ".txt.gz")
      val fileSink: Sink[ByteString, Future[IOResult]] = Flow[ByteString]
        .toMat(FileIO.toPath(Paths.get(tmpfile.getPath)))(Keep.right)

      val future: Future[IOResult] = source
        .via(flow)
        .runWith(fileSink)

      whenReady(future) { _ =>
        // Unzip the file, then open it and check it contains the strings
        // we'd expect.  Note that files have a trailing newline.
        val fileContents = readGzipFile(tmpfile.getPath)
        val expectedContents = expectedLines.mkString("\n") + "\n"

        fileContents shouldBe expectedContents
      }
    }
  }
}
