package weco.pipeline.ingestor.fixtures

import io.circe.Json
import io.circe.syntax._
import org.apache.commons.io.FileUtils
import weco.catalogue.internal_model.work.generators.InstantGenerators
import weco.json.JsonUtil._

import java.io.{File, PrintWriter}
import java.time.Instant
import scala.util.{Random, Success}

trait ExampleDocumentUtils extends InstantGenerators {
  case class ExampleDocument(description: String, createdAt: Instant = Instant.now(), id: String, document: Json)

  override protected lazy val random: Random =
    new Random(0)

  // For the tests in which we'll be using these documents, we don't care about
  // the actual value of these Instants; we just care that they're deterministic.
  override def instantInLast30Days: Instant =
    Instant.parse("2020-10-15T15:51:00.00Z")

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

  def saveDocuments(documents: Seq[(String, ExampleDocument)]): Unit =
    documents.foreach { case (id, doc) =>
      val file = new File(s"pipeline/ingestor/example_documents/$id.json")

      val isAlreadyUpToDate =
        if (file.exists()) {
          val existingContents = FileUtils.readFileToString(file, "UTF-8")

          fromJson[ExampleDocument](existingContents) match {
            case Success(ExampleDocument(existingDescription, _, existingId, existingDocument)) =>
              existingDescription == doc.description && existingId == doc.id && existingDocument == doc.document

            case _ => false
          }
        } else {
          false
        }

      if (!isAlreadyUpToDate) {
        val pw = new PrintWriter(file)
        pw.write(doc.asJson.spaces2)
        pw.close()
      }
    }
}
