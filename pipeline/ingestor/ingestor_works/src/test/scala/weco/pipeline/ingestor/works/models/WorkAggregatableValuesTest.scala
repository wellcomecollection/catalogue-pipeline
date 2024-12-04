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
import weco.pipeline.ingestor.common.models.AggregatableField
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
      workTypes = List(AggregatableField("k", "Pictures")),
      genres = List(
        AggregatableField("h5fvmn9u", "Ink drawings"),
        AggregatableField("tgxvuh8x", "Drawings")
      ),
      productionDates = List(
        AggregatableField.fromLabel("1970")
      ),
      subjects = List(
        AggregatableField("bse2dtxc", "Jungian psychology"),
        AggregatableField("hjw49bkh", "Dreams"),
        AggregatableField(
          "wfkwqmmx",
          "McGlashan, Alan Fleming, 1898-1997"
        )
      ),
      languages = List(
        AggregatableField("eng", "English")
      ),
      contributors = List(
        AggregatableField("npanm646", "M.A.C.T"),
        AggregatableField("wfkwqmmx", "McGlashan, Alan Fleming, 1898-1997")
      ),
      itemLicenses = List(
        AggregatableField("cc-by", "Attribution 4.0 International (CC BY 4.0)"),
      ),
      availabilities = List(
        AggregatableField("closed-stores", "Closed stores"),
        AggregatableField("online", "Online"),
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
      AggregatableField("chi", "Chinese"),
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
        AggregatableField.fromLabel("salty sandwiches"),
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
        AggregatableField.fromLabel("Pablo Picasso"),
      )
    }
  }
}
