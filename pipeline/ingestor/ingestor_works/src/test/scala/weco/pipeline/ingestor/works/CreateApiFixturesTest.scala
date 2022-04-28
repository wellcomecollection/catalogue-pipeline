package weco.pipeline.ingestor.works

import io.circe.syntax._
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.json.JsonUtil._
import weco.pipeline.ingestor.fixtures.ApiFixtures

class CreateApiFixturesTest
    extends AnyFunSpec
    with Matchers
    with WorkGenerators
    with ApiFixtures {
  it("creates the fixtures") {
    val fixtures = denormalisedWorks(count = 5)
      .sortBy { _.state.canonicalId }
      .zipWithIndex
      .map {
        case (work, index) =>
          s"list-of-works.$index" -> Fixture(
            description = "one of a list of works",
            id = work.id,
            document = WorkTransformer.deriveData(work).asJson
          )
      }

    saveFixtures(fixtures)
  }
}
