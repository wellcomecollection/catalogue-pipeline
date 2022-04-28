package weco.pipeline.ingestor.fixtures

import io.circe.Json
import io.circe.syntax._
import weco.fixtures.RandomGenerators
import weco.json.JsonUtil._

import java.io.{File, PrintWriter}
import java.time.Instant
import scala.util.Random

trait ExampleDocumentUtils extends RandomGenerators {
  case class SyntheticDocument(description: String, createdAt: Instant = Instant.now(), id: String, document: Json)

  override protected lazy val random: Random =
    new Random(0)

  private def writeReadme(): Unit = {
    val file = new File("pipeline/ingestor/example_documents/README.md")
    val pw = new PrintWriter(file)
    pw.write(
      """
        |# example_documents
        |
        |This folder contains a collection of randomly generated documents that look like
        |the documents in the API index.
        |
        |They're meant for use in the API tests -- these JSON files get copied into the
        |API repo, then they can be loaded into an Elasticsearch index to ensure the API
        |can query them correctly.
        |""".stripMargin.trim)
    pw.close()
  }

  writeReadme()

  def saveDocuments(fixtures: List[(String, SyntheticDocument)]): Unit =
    fixtures.foreach { case (id, fixture) =>
      val file = new File(s"pipeline/ingestor/example_documents/$id.json")
      val pw = new PrintWriter(file)
      pw.write(fixture.asJson.spaces2)
      pw.close()
    }
}
