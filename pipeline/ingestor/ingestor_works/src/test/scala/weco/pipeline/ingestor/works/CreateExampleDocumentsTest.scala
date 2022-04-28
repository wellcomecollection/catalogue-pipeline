package weco.pipeline.ingestor.works

import io.circe.syntax._
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.json.JsonUtil._
import weco.pipeline.ingestor.fixtures.ExampleDocumentUtils

class CreateExampleDocumentsTest extends AnyFunSpec with Matchers with WorkGenerators with ExampleDocumentUtils {
  it("creates the example documents") {
    val fixtures = denormalisedWorks(count = 5)
      .zipWithIndex
      .map { case (work, index) =>
        s"list-of-works.$index" -> SyntheticDocument(
          description = "one of a list of works",
          id = work.id,
          document = WorkTransformer.deriveData(work).asJson
        )
      }

    saveDocuments(fixtures)
  }
}
