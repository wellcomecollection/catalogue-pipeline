package weco.pipeline.transformer.marc_common.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.{
  Concept,
  Place,
  ProductionEvent
}
import weco.fixtures.RandomGenerators
import weco.pipeline.transformer.marc_common.generators.MarcTestRecord
import weco.pipeline.transformer.marc_common.models.{
  MarcControlField,
  MarcField,
  MarcSubfield
}
import weco.pipeline.transformer.transformers.ParsedPeriod

class MarcProductionTest
    extends AnyFunSpec
    with Matchers
    with RandomGenerators {

  it("returns an empty list if neither 260 nor 264 are present") {
    MarcProduction(MarcTestRecord(fields = List())) shouldBe List()
  }

  describe("Both MARC field 260 and 264") {
    it(
      "if both 260 and 264 are present, accept 260"
    ) {
      MarcProduction(
        MarcTestRecord(
          controlFields = List(
            MarcControlField(
              marcTag = "001",
              content = randomAlphanumeric(length = 9)
            )
          ),
          fields = List(
            MarcField(
              marcTag = "260",
              subfields = List(
                MarcSubfield(tag = "a", content = "Paris")
              )
            ),
            MarcField(
              marcTag = "264",
              indicator2 = "0",
              subfields = List(
                MarcSubfield(tag = "a", content = "London")
              )
            )
          )
        )
      ) shouldBe List(
        ProductionEvent(
          label = "Paris",
          places = List(Place("Paris")),
          agents = List(),
          dates = List(),
          function = None
        )
      )
    }
  }

  describe("MARC field 008") {
    val marcControlField008 = MarcControlField(
      marcTag = "008",
      content = "790922s1757    enk||||      o00||||eng ccam   "
    )

    it("uses field 008 if neither 260 or 264 are present") {
      MarcProduction(
        MarcTestRecord(
          controlFields = List(marcControlField008),
          fields = List()
        )
      ) shouldBe List(
        ProductionEvent(
          label = "1757",
          places = List(Place("England")),
          agents = List(),
          dates = List(ParsedPeriod("1757")),
          function = None
        )
      )
    }

    it("ignores field 008 if 264 is present") {
      MarcProduction(
        MarcTestRecord(
          controlFields = List(marcControlField008),
          fields = List(
            MarcField(
              marcTag = "264",
              indicator2 = "1",
              subfields = List(
                MarcSubfield(tag = "c", content = "2002"),
                MarcSubfield(tag = "a", content = "London")
              )
            )
          )
        )
      ) shouldBe List(
        ProductionEvent(
          label = "2002 London",
          places = List(Place("London")),
          agents = List(),
          dates = List(ParsedPeriod("2002")),
          function = Some(Concept("Publication"))
        )
      )
    }

    it("ignores field 008 if 260 is present") {
      MarcProduction(
        MarcTestRecord(
          controlFields = List(marcControlField008),
          fields = List(
            MarcField(
              marcTag = "260",
              indicator2 = "1",
              subfields = List(
                MarcSubfield(tag = "c", content = "2002"),
                MarcSubfield(tag = "a", content = "London")
              )
            )
          )
        )
      ) shouldBe List(
        ProductionEvent(
          label = "2002 London",
          places = List(Place("London")),
          agents = List(),
          dates = List(ParsedPeriod("2002")),
          function = None
        )
      )
    }

    it("uses date information from 008 if not present in 260/264") {
      MarcProduction(
        MarcTestRecord(
          controlFields = List(marcControlField008),
          fields = List(
            MarcField(
              marcTag = "260",
              indicator2 = "1",
              subfields = List(
                MarcSubfield(tag = "a", content = "London")
              )
            )
          )
        )
      ) shouldBe List(
        ProductionEvent(
          label = "London",
          places = List(Place("London")),
          agents = List(),
          dates = List(ParsedPeriod("1757")),
          function = None
        )
      )
    }

    // Example is from b28533306, with the 264 field modified so it's never parseable
    // and so this test is predictable.
    it(
      "uses date information from 008 but labels from 260/4 if the latter cannot be parsed"
    ) {
      MarcProduction(
        MarcTestRecord(
          controlFields = List(
            MarcControlField(
              marcTag = "008",
              content = "160323s1972    enk               ku    d"
            )
          ),
          fields = List(
            MarcField(
              marcTag = "264",
              indicator2 = "0",
              subfields = List(
                MarcSubfield(tag = "a", content = "[Netherne, Surrey],"),
                MarcSubfield(
                  tag = "c",
                  content = "B̷A̴D̸ ̴U̶N̸P̵A̸R̸S̷E̷A̶B̵L̶E̸ ̵N̴O̴N̶S̵E̷N̷S̴E̴"
                )
              )
            )
          )
        )
      ) shouldBe List(
        ProductionEvent(
          label =
            "[Netherne, Surrey], B̷A̴D̸ ̴U̶N̸P̵A̸R̸S̷E̷A̶B̵L̶E̸ ̵N̴O̴N̶S̵E̷N̷S̴E̴",
          places = List(Place("[Netherne, Surrey],")),
          agents = List(),
          dates = List(
            ParsedPeriod("1972").copy(
              label = "B̷A̴D̸ ̴U̶N̸P̵A̸R̸S̷E̷A̶B̵L̶E̸ ̵N̴O̴N̶S̵E̷N̷S̴E̴"
            )
          ),
          function = Some(Concept("Production"))
        )
      )
    }
  }
}
