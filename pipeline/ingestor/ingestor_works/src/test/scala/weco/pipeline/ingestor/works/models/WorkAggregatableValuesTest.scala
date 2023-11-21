package weco.pipeline.ingestor.works.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{DataState, IdState}
import weco.catalogue.internal_model.languages.Language
import weco.catalogue.internal_model.work.generators.{
  ItemsGenerators,
  PeriodGenerators,
  ProductionEventGenerators
}
import weco.catalogue.internal_model.work._
import weco.pipeline.ingestor.models.IngestorTestData

class WorkAggregatableValuesTest
    extends AnyFunSpec
    with Matchers
    with IngestorTestData
    with ItemsGenerators
    with ProductionEventGenerators
    with PeriodGenerators {

  lazy val testWorkAggregatableValues: WorkAggregatableValues =
    WorkAggregatableValues(testWork)

  it("creates aggregatable values") {
    testWorkAggregatableValues shouldBe WorkAggregatableValues(
      workTypes = List("""{"id":"k","label":"Pictures","type":"Format"}"""),
      genres = List(
        """{"label":"Ink drawings","concepts":[],"type":"Genre"}""",
        """{"label":"Drawings","concepts":[],"type":"Genre"}"""
      ),
      productionDates = List("""{"label":"1970","type":"Period"}"""),
      subjects = List(
        """{"label":"Jungian psychology","concepts":[],"type":"Subject"}""",
        """{"label":"Dreams","concepts":[],"type":"Subject"}""",
        """{"label":"McGlashan, Alan Fleming, 1898-1997","concepts":[],"type":"Subject"}"""
      ),
      languages = List("""{"id":"eng","label":"English","type":"Language"}"""),
      contributors = List(
        """{"id":"npanm646","label":"M.A.C.T","type":"Person"}""",
        """{"id":"wfkwqmmx","label":"McGlashan, Alan Fleming, 1898-1997","type":"Person"}"""
      ),
      itemLicenses = List(
        """{"id":"cc-by","label":"Attribution 4.0 International (CC BY 4.0)","url":"http://creativecommons.org/licenses/by/4.0/","type":"License"}"""
      ),
      availabilities = List(
        """{"id":"closed-stores","label":"Closed stores","type":"Availability"}""",
        """{"id":"online","label":"Online","type":"Availability"}"""
      )
    )
  }

  it("removes identifiers from subjects") {
    val aggregatableValues = WorkAggregatableValues(
      testWork.copy[WorkState.Denormalised](data =
        testWork.data.copy[DataState.Identified](subjects =
          List(
            Subject(
              id = IdState.Identified(
                canonicalId = createCanonicalId,
                sourceIdentifier = createSourceIdentifier
              ),
              label = "impish indicators"
            ),
            Subject(
              id = IdState.Identified(
                canonicalId = createCanonicalId,
                sourceIdentifier = createSourceIdentifier
              ),
              label = "ill-fated ideas"
            ),
            Subject(label = "illicit implications", concepts = List())
          )
        )
      )
    )

    aggregatableValues.subjects shouldBe List(
      """{"label":"impish indicators","concepts":[],"type":"Subject"}""",
      """{"label":"ill-fated ideas","concepts":[],"type":"Subject"}""",
      """{"label":"illicit implications","concepts":[],"type":"Subject"}"""
    )
  }

  it("normalises language values") {
    val aggregatableValues = WorkAggregatableValues(
      testWork.copy(data =
        testWork.data.copy(languages =
          List(
            Language(id = "chi", label = "Chinese"),
            Language(id = "chi", label = "Mandarin")
          )
        )
      )
    )

    aggregatableValues.languages shouldBe List(
      """{"id":"chi","label":"Chinese","type":"Language"}"""
    )
  }

  describe("normalising labels") {
    info(
      "labels are normalised in aggregations to remove punctuation that is not deliberately contrastive"
    )
    it("normalises subject labels") {
      WorkAggregatableValues(
        testWork.copy[WorkState.Denormalised](data =
          testWork.data.copy[DataState.Identified](subjects =
            List(
              Subject(
                label = "salty sandwiches.",
                concepts = Nil
              )
            )
          )
        )
      ).subjects shouldBe List(
        """{"label":"salty sandwiches","concepts":[],"type":"Subject"}"""
      )
    }

    it("normalises contributor labels") {
      WorkAggregatableValues(
        testWork.copy[WorkState.Denormalised](data =
          testWork.data.copy[DataState.Identified](contributors =
            List(
              Contributor(
                id = IdState.Unidentifiable,
                agent = Person(label = "Pablo Picasso."),
                roles = List(ContributionRole("painter"))
              )
            )
          )
        )
      ).contributors shouldBe List(
        """{"label":"Pablo Picasso","type":"Person"}"""
      )
    }
  }
}
