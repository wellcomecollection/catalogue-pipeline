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
import weco.pipeline.ingestor.common.models.AggregatableIdLabel
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
      workTypes = List(AggregatableIdLabel(Some("k"), "Pictures")),
      genres = List(
        AggregatableIdLabel(Some("h5fvmn9u"), "Ink drawings"),
        AggregatableIdLabel(Some("tgxvuh8x"), "Drawings")
      ),
      productionDates = List(
        AggregatableIdLabel(None: Option[String], "1970")
      ),
      subjects = List(
        AggregatableIdLabel(Some("bse2dtxc"), "Jungian psychology"),
        AggregatableIdLabel(Some("hjw49bkh"), "Dreams"),
        AggregatableIdLabel(
          Some("wfkwqmmx"),
          "McGlashan, Alan Fleming, 1898-1997"
        )
      ),
      languages = List(
        AggregatableIdLabel(Some("eng"), "English")
      ),
      contributors = List(
        AggregatableIdLabel(Some("npanm646"), "M.A.C.T"),
        AggregatableIdLabel(Some("wfkwqmmx"), "McGlashan, Alan Fleming, 1898-1997")
      ),
      itemLicenses = List(
        AggregatableIdLabel(Some("cc-by"), "Attribution 4.0 International (CC BY 4.0)"),
      ),
      availabilities = List(
        AggregatableIdLabel(Some("closed-stores"), "Closed stores"),
        AggregatableIdLabel(Some("online"), "Online"),
      )
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
      AggregatableIdLabel(Some("chi"), "Chinese"),
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
        AggregatableIdLabel(None, "salty sandwiches"),
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
        AggregatableIdLabel(None, "Pablo Picasso"),
      )
    }
  }
}
