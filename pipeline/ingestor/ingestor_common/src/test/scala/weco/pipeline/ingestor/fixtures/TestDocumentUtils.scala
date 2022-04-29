package weco.pipeline.ingestor.fixtures

import io.circe.Json
import io.circe.syntax._
import org.apache.commons.io.FileUtils
import weco.catalogue.internal_model.work.generators.InstantGenerators
import weco.fixtures.RandomGenerators
import weco.json.JsonUtil._

import java.io.{File, PrintWriter}
import java.time.Instant
import scala.util.{Random, Success}

trait TestDocumentUtils extends InstantGenerators with RandomGenerators {
  case class TestDocument(description: String, createdAt: Instant = Instant.now(), id: String, document: Json)

  override protected lazy val random: Random =
    new Random(0)

  val testDocumentsRoot = "pipeline/ingestor/test_documents"

  private def writeReadme(): Unit = {
    val file = new File(s"$testDocumentsRoot/README.md")
    val pw = new PrintWriter(file)
    pw.write(
      """
        |# test_documents
        |
        |This folder contains a collection of randomly generated documents that look like
        |the documents in the API index.
        |
        |They're meant for use in the API tests -- these JSON files get copied into the
        |API repo, then they can be loaded into an Elasticsearch index to ensure the API
        |can query them correctly.
        |
        |These files are automatically generated by the ingestor tests.  Don't edit them
        |manually -- update the tests, then re-run them to generate new documents.
        |""".stripMargin.trim)
    pw.close()
  }

  writeReadme()

  def saveDocuments(documents: Seq[(String, TestDocument)]): Unit =
    documents.foreach { case (id, doc) =>
      val file = new File(s"$testDocumentsRoot/$id.json")

      // To avoid endless churn, check if an existing test document already exists.
      //
      // If it does, and the only difference is the date it was created, we don't need to
      // recreate it here -- skip writing a new file.
      val isAlreadyUpToDate =
        if (file.exists()) {
          val existingContents = FileUtils.readFileToString(file, "UTF-8")

          fromJson[TestDocument](existingContents) match {
            case Success(TestDocument(existingDescription, _, existingId, existingDocument)) =>
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
