package weco.pipeline.transformer.marc_common.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.{
  Agent,
  Concept,
  Place,
  ProductionEvent
}
import weco.fixtures.RandomGenerators
import weco.pipeline.transformer.marc_common.exceptions.CataloguingException
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
      "throws a cataloguing error if both 260 and 264 are present, and 264 has a 2nd indicator"
    ) {
      val bibId = randomAlphanumeric(length = 9)
      val caught = intercept[CataloguingException] {
        MarcProduction(
          MarcTestRecord(
            controlFields = List(
              MarcControlField(
                marcTag = "001",
                content = bibId
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
        )
      }

      caught.getMessage should startWith("Problem in the data")
      caught.getMessage should include(bibId)
      caught.getMessage should include("Record has both 260 and 264 fields")
    }

    it("uses 260 if 264 only contains a copyright statement in subfield c") {
      MarcProduction(
        MarcTestRecord(
          fields = List(
            MarcField(
              marcTag = "260",
              subfields = List(
                MarcSubfield(tag = "a", content = "San Francisco :"),
                MarcSubfield(
                  tag = "b",
                  content = "Morgan Kaufmann Publishers,"
                ),
                MarcSubfield(tag = "c", content = "2004")
              )
            ),
            MarcField(
              marcTag = "264",
              subfields = List(
                MarcSubfield(tag = "c", content = "©2004")
              )
            )
          )
        )
      ) shouldBe List(
        ProductionEvent(
          label = "San Francisco : Morgan Kaufmann Publishers, 2004",
          places = List(Place("San Francisco")),
          agents = List(
            Agent("Morgan Kaufmann Publishers")
          ),
          dates = List(ParsedPeriod("2004")),
          function = None
        )
      )
    }

    it("returns correctly if 260 and 264 contain the same subfields") {
      val matchingSubfields = List(
        MarcSubfield(tag = "a", content = "London :"),
        MarcSubfield(tag = "b", content = "Wellcome Trust,"),
        MarcSubfield(tag = "c", content = "1992")
      )

      MarcProduction(
        MarcTestRecord(
          fields = List(
            MarcField(
              marcTag = "260",
              subfields = matchingSubfields
            ),
            MarcField(
              marcTag = "264",
              subfields = matchingSubfields
            )
          )
        )
      ) shouldBe List(
        ProductionEvent(
          label = "London : Wellcome Trust, 1992",
          places = List(Place("London")),
          agents = List(Agent("Wellcome Trust")),
          dates = List(ParsedPeriod("1992")),
          function = None
        )
      )
    }

    // Based on b31500018, as retrieved on 28 March 2019
    it("returns correctly if the 264 subfields only contain punctuation") {
      MarcProduction(
        MarcTestRecord(
          fields = List(
            MarcField(
              marcTag = "260",
              subfields = List(
                MarcSubfield(tag = "c", content = "2019")
              )
            ),
            MarcField(
              marcTag = "264",
              subfields = List(
                MarcSubfield(tag = "a", content = ":"),
                MarcSubfield(tag = "b", content = ","),
                MarcSubfield(tag = "c", content = "")
              )
            )
          )
        )
      ) shouldBe List(
        ProductionEvent(
          label = "2019",
          places = List(),
          agents = List(),
          dates = List(ParsedPeriod("2019")),
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
