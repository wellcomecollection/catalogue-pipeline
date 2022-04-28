package weco.pipeline.ingestor.works

import io.circe.Json
import io.circe.syntax._
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{
  InstantRange,
  Period,
  ProductionEvent,
  Work,
  WorkState
}
import weco.catalogue.internal_model.work.generators.{
  ItemsGenerators,
  WorkGenerators
}
import weco.json.JsonUtil._
import weco.pipeline.ingestor.fixtures.ExampleDocumentUtils

import java.time.{Instant, LocalDate, Month}

trait PeriodGenerators {
  def createPeriodForYear(year: String): Period[IdState.Minted] =
    Period(
      id = IdState.Unidentifiable,
      label = year,
      range = Some(
        InstantRange(
          from = LocalDate.of(year.toInt, Month.JANUARY, 1),
          to = LocalDate.of(year.toInt, Month.DECEMBER, 31),
          label = year
        )
      )
    )
}

/** Creates the example documents we use in teh API tests.
  *
  * These tests use a seeded RNG to ensure deterministic results; to prevent
  * regenerating existing examples and causing unnecessary churn in the API tests
  * when values change, I suggest adding new examples at the bottom of this file.
  */
class CreateExampleDocumentsTest
    extends AnyFunSpec
    with Matchers
    with WorkGenerators
    with ItemsGenerators
    with PeriodGenerators
    with ExampleDocumentUtils {
  it("creates the example documents") {
    saveWorks(
      works = (1 to 5).map(_ => denormalisedWork()),
      description = "an arbitrary list of visible works",
      id = "works.visible"
    )
    saveWorks(
      works = (1 to 3).map(_ => denormalisedWork().invisible()),
      description = "an arbitrary list of invisible works",
      id = "works.invisible"
    )
    saveWorks(
      works = (1 to 2).map(
        _ =>
          denormalisedWork().redirected(
            redirectTarget = IdState.Identified(
              canonicalId = createCanonicalId,
              sourceIdentifier = createSourceIdentifier
            )
        )),
      description = "an arbitrary list of redirected works",
      id = "works.redirected"
    )
    saveWorks(
      works = (1 to 4).map(_ => denormalisedWork().deleted()),
      description = "an arbitrary list of deleted works",
      id = "works.deleted"
    )

    saveWork(
      work = denormalisedWork()
        .edition("Special edition")
        .duration(3600),
      description = "a work with optional top-level fields",
      id = "work-with-edition-and-duration"
    )

    saveWork(
      work = denormalisedWork().title("A drawing of a dodo"),
      description = "a work with 'dodo' in the title",
      id = "work-title-dodo"
    )
    saveWork(
      work = denormalisedWork().title("A mezzotint of a mouse"),
      description = "a work with 'mouse' in the title",
      id = "work-title-mouse"
    )

    saveWork(
      work = denormalisedWork().thumbnail(createDigitalLocation),
      description = "a work with a thumbnail",
      id = "work-thumbnail"
    )

    Seq("1900", "1976", "1904", "2020", "1098").map { year =>
      saveWork(
        work = denormalisedWork()
          .production(
            List(
              ProductionEvent(
                label = randomAlphanumeric(25),
                places = List(),
                agents = List(),
                dates = List(createPeriodForYear(year))
              )
            )
          )
          .title(s"Production event in $year"),
        description = s"a work with a production event in $year",
        id = s"work-production.$year"
      )
    }
  }

  private def saveWork(
    work: Work[WorkState.Denormalised],
    description: String,
    id: String
  ): Unit =
    saveWorks(works = List(work), description, id)

  private def saveWorks(
    works: Seq[Work[WorkState.Denormalised]],
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
      works.zipWithIndex
        .map {
          case (work, index) =>
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
        override protected def getIndexedTime: Instant =
          Instant.parse("2020-10-15T15:51:00.00Z")
      }

      transformer.deriveData(work).asJson
    }
  }
}
