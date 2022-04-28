package weco.pipeline.ingestor.works

import io.circe.Json
import io.circe.syntax._
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.json.JsonUtil._
import weco.pipeline.ingestor.fixtures.ExampleDocumentUtils

import java.time.Instant

class CreateExampleDocumentsTest extends AnyFunSpec with Matchers with WorkGenerators with ExampleDocumentUtils {
  it("creates the example documents") {
    saveWorks(
      works = denormalisedWorks(count = 5),
      description = "an arbitrary list of works",
      id = "list-of-works"
    )

    saveWork(
      work = denormalisedWork()
        .edition("Special edition")
        .duration(3600),
      description = "a work with optional top-level fields",
      id = "work-with-edition-and-duration"
    )
  }

  private def saveWork(
    work: Work[WorkState.Denormalised],
    description: String,
    id: String
  ): Unit =
    saveWorks(works = List(work), description, id)

  private def saveWorks(
    works: List[Work[WorkState.Denormalised]],
    description: String,
    id: String
  ): Unit = {
    val documents = if (works.length == 1) {
      val work = works.head

      Seq(
        id -> ExampleDocument(
          description,
          id = work.id,
          document = work.toDocument
        )
      )
    } else {
      works
        .zipWithIndex
        .map { case (work, index) =>
          s"$id.$index" -> ExampleDocument(
            description,
            id = work.id,
            document = work.toDocument
          )
        }
    }

    saveDocuments(documents)
  }

  implicit class WorkOps(work: Work[WorkState.Denormalised]) {
    def toDocument: Json = {
      // This is a fixed date so we get consistent values in the indexedTime
      // field in the generated documents.
      val transformer = new WorkTransformer {
        override protected def getIndexedTime: Instant = {
          println(s"@@AWLC calling my getIndexedTime")
          Instant.parse("2020-10-15T15:51:00.00Z")
        }
      }

      transformer.deriveData(work).asJson
    }
  }
}
