package weco.pipeline.ingestor.works

import io.circe.syntax._
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.json.JsonUtil._
import weco.pipeline.ingestor.fixtures.ExampleDocumentUtils

class CreateExampleDocumentsTest extends AnyFunSpec with Matchers with WorkGenerators with ExampleDocumentUtils {
  it("creates the example documents") {
    saveWorks(
      works = denormalisedWorks(count = 5),
      description = "an arbitrary list of works",
      id = "list-of-works"
    )
  }

  private def saveWorks(
    works: List[Work[WorkState.Denormalised]],
    description: String,
    id: String
  ): Unit = {
    val documents = if (works.length == 1) {
      Seq(
        id -> ExampleDocument(
          description,
          id = works.head.id,
          document = WorkTransformer.deriveData(works.head).asJson
        )
      )
    } else {
      works
        .zipWithIndex
        .map { case (work, index) =>
          s"$id.$index" -> ExampleDocument(
            description,
            id = work.id,
            document = WorkTransformer.deriveData(work).asJson
          )
        }
    }

    saveDocuments(documents)
  }
}
