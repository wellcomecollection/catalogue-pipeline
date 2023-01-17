package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{
  Agent,
  Concept,
  Place,
  ProductionEvent
}
import weco.pipeline.transformer.sierra.exceptions.CataloguingException
import weco.pipeline.transformer.transformers.ParsedPeriod
import weco.sierra.generators.{MarcGenerators, SierraDataGenerators}
import weco.sierra.models.marc.{Subfield, VarField}

class SierraProductionTest
    extends AnyFunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {

  it("returns an empty list if neither 260 nor 264 are present") {
    transformToProduction(varFields = List()) shouldBe List()
  }

  // Examples are taken from the MARC spec for field 260.
  // https://www.loc.gov/marc/bibliographic/bd260.html

  describe("MARC field 260") {
    it("populates places from subfield a") {
      val production = transform260ToProduction(
        subfields = List(
          Subfield(tag = "a", content = "Paris"),
          Subfield(tag = "a", content = "London")
        ))

      production.places shouldBe List(
        Place(label = "Paris"),
        Place(label = "London")
      )
    }

    it("populates agents from subfield b") {
      val production = transform260ToProduction(
        subfields = List(
          Subfield(tag = "b", content = "Gauthier-Villars ;"),
          Subfield(tag = "b", content = "Vogue")
        ))

      production.agents shouldBe List(
        Agent(label = "Gauthier-Villars ;"),
        Agent(label = "Vogue")
      )
    }

    it("populates dates from subfield c") {
      val production = transform260ToProduction(
        subfields = List(
          Subfield(tag = "c", content = "1955"),
          Subfield(tag = "c", content = "1984"),
          Subfield(tag = "c", content = "1999")
        ))

      production.dates shouldBe List(
        ParsedPeriod(label = "1955"),
        ParsedPeriod(label = "1984"),
        ParsedPeriod(label = "1999")
      )
    }

    it("sets the function as None if it only has subfields a/b/c") {
      val production = transform260ToProduction(
        subfields = List(
          Subfield(tag = "a", content = "New York"),
          Subfield(tag = "b", content = "Xerox Films"),
          Subfield(tag = "c", content = "1973")
        ))

      production.function shouldBe None
    }

    it("populates places from a and e, and sets the function as Manufacture") {
      val production = transform260ToProduction(
        subfields = List(
          Subfield(tag = "a", content = "New York, N.Y."),
          Subfield(tag = "e", content = "Reston, Va."),
          Subfield(tag = "e", content = "[Philadelphia]"),
        ))

      production.places shouldBe List(
        Place(label = "New York, N.Y."),
        Place(label = "Reston, Va."),
        Place(label = "[Philadelphia]")
      )

      production.function shouldBe Some(Concept("Manufacture"))
    }

    it("populates agents from b and f, and sets the function as Manufacture") {
      val production = transform260ToProduction(
        subfields = List(
          Subfield(tag = "b", content = "Macmillan"),
          Subfield(tag = "f", content = "Sussex Tapes"),
          Subfield(tag = "f", content = "US Dept of Energy"),
        ))

      production.agents shouldBe List(
        Agent(label = "Macmillan"),
        Agent(label = "Sussex Tapes"),
        Agent(label = "US Dept of Energy")
      )

      production.function shouldBe Some(Concept("Manufacture"))
    }

    it("populates dates from c and g, and sets the function as Manufacture") {
      val production = transform260ToProduction(
        subfields = List(
          Subfield(tag = "c", content = "1981"),
          Subfield(tag = "g", content = "April 15, 1977"),
          Subfield(tag = "g", content = "1973 printing")
        ))

      production.dates shouldBe List(
        ParsedPeriod(label = "1981"),
        ParsedPeriod(label = "April 15, 1977"),
        ParsedPeriod(label = "1973 printing")
      )

      production.function shouldBe Some(Concept("Manufacture"))
    }

    it("picks up multiple instances of the 260 field") {
      val varFields = List(
        VarField(
          marcTag = "260",
          subfields = List(
            Subfield(tag = "a", content = "London :"),
            Subfield(tag = "b", content = "Arts Council of Great Britain,"),
            Subfield(tag = "c", content = "1976;"),
            Subfield(tag = "e", content = "Twickenham :"),
            Subfield(tag = "f", content = "CTD Printers,"),
            Subfield(tag = "g", content = "1974")
          )
        ),
        VarField(
          marcTag = "260",
          subfields = List(
            Subfield(tag = "a", content = "Bethesda, Md. :"),
            Subfield(
              tag = "b",
              content =
                "Toxicology Information Program, National Library of Medicine [producer] ;"),
            Subfield(tag = "a", content = "Springfield, Va. :"),
            Subfield(
              tag = "b",
              content = "National Technical Information Service [distributor],"),
            Subfield(tag = "c", content = "1974-")
          )
        )
      )

      val expectedProductions = List(
        ProductionEvent(
          label =
            "London : Arts Council of Great Britain, 1976; Twickenham : CTD Printers, 1974",
          places = List(Place("London"), Place("Twickenham")),
          agents = List(
            Agent("Arts Council of Great Britain"),
            Agent("CTD Printers")
          ),
          dates = List(ParsedPeriod("1976;"), ParsedPeriod("1974")),
          function = Some(Concept("Manufacture"))
        ),
        ProductionEvent(
          label =
            "Bethesda, Md. : Toxicology Information Program, National Library of Medicine [producer] ; Springfield, Va. : National Technical Information Service [distributor], 1974-",
          places = List(Place("Bethesda, Md."), Place("Springfield, Va.")),
          agents = List(
            Agent(
              "Toxicology Information Program, National Library of Medicine [producer] ;"),
            Agent("National Technical Information Service [distributor]")
          ),
          dates = List(ParsedPeriod("1974-")),
          function = None
        )
      )

      transformToProduction(varFields) shouldBe expectedProductions
    }

    it("normalises Place and Period labels") {
      val production = transform260ToProduction(
        subfields = List(
          Subfield(tag = "a", content = "Paris  : "),
          Subfield(tag = "a", content = "London :"),
          Subfield(tag = "c", content = "1984 . "),
          Subfield(tag = "c", content = "1999.")
        ))

      production.places shouldBe List(
        Place(label = "Paris"),
        Place(label = "London")
      )

      production.dates shouldBe List(
        ParsedPeriod(label = "1984"),
        ParsedPeriod(label = "1999")
      )
    }
  }

  // Examples are taken from the MARC spec for field 264.
  // https://www.loc.gov/marc/bibliographic/bd264.html

  describe("MARC field 264") {
    it("populates places from subfield a") {
      val production = transform264ToProduction(
        subfields = List(
          Subfield(tag = "a", content = "Boston"),
          Subfield(tag = "a", content = "Cambridge")
        ))

      production.places shouldBe List(
        Place(label = "Boston"),
        Place(label = "Cambridge")
      )
    }

    it("populates agents from subfield b") {
      val production = transform264ToProduction(
        subfields = List(
          Subfield(tag = "b", content = "ABC Publishers"),
          Subfield(tag = "b", content = "Iverson Company")
        ))

      production.agents shouldBe List(
        Agent(label = "ABC Publishers"),
        Agent(label = "Iverson Company")
      )
    }

    it("populates dates from subfield c") {
      val production = transform264ToProduction(
        subfields = List(
          Subfield(tag = "c", content = "2002"),
          Subfield(tag = "c", content = "1983"),
          Subfield(tag = "c", content = "copyright 2005")
        ))

      production.dates shouldBe List(
        ParsedPeriod(label = "2002"),
        ParsedPeriod(label = "1983"),
        ParsedPeriod(label = "copyright 2005")
      )
    }

    describe("production function") {
      it("sets Production from 2nd indicator == 0") {
        checkProductionFunctionFor264(
          indicator2 = "0",
          expectedFunction = "Production")
      }

      it("sets Publication from 2nd indicator == 1") {
        checkProductionFunctionFor264(
          indicator2 = "1",
          expectedFunction = "Publication")
      }

      it("sets Distribution from 2nd indicator == 2") {
        checkProductionFunctionFor264(
          indicator2 = "2",
          expectedFunction = "Distribution")
      }

      it("sets Manufacture from 2nd indicator == 3") {
        checkProductionFunctionFor264(
          indicator2 = "3",
          expectedFunction = "Manufacture")
      }

      it("throws an error if the 2nd indicator is unrecognised") {
        val varFields = List(
          createVarFieldWith(
            marcTag = "264",
            indicator2 = "x",
            subfields = List()
          )
        )

        val bibData = createSierraBibDataWith(varFields = varFields)
        val bibId = createSierraBibNumber

        val caught = intercept[CataloguingException] {
          SierraProduction(bibId, bibData)
        }

        caught.getMessage should startWith("Problem in the Sierra data")
        caught.getMessage should include(bibId.withoutCheckDigit)
        caught.getMessage should include(
          "Unrecognised second indicator for production function")
      }
    }

    it(
      "ignores instances of the 264 field related to copyright (2nd indicator 4)") {
      val varFields = List(
        createVarFieldWith(
          marcTag = "264",
          indicator2 = "4",
          subfields = List(
            Subfield(tag = "c", content = "copyright 2005")
          )
        ),
        createVarFieldWith(
          marcTag = "264",
          indicator2 = "3",
          subfields = List(
            Subfield(tag = "a", content = "Cambridge :"),
            Subfield(tag = "b", content = "Kinsey Printing Company")
          )
        )
      )

      val expectedProductions = List(
        ProductionEvent(
          label = "Cambridge : Kinsey Printing Company",
          places = List(Place("Cambridge")),
          agents = List(Agent("Kinsey Printing Company")),
          dates = List(),
          function = Some(Concept("Manufacture"))
        )
      )

      transformToProduction(varFields) shouldBe expectedProductions
    }

    it("ignores instances of the 264 field with an empty 2nd indicator") {
      val varFields = List(
        createVarFieldWith(
          marcTag = "264",
          indicator2 = " ",
          subfields = List(
            Subfield(tag = "c", content = "copyright 2005")
          )
        ),
        createVarFieldWith(
          marcTag = "264",
          indicator2 = "3",
          subfields = List(
            Subfield(tag = "a", content = "London :"),
            Subfield(tag = "b", content = "Wellcome Collection Publishing")
          )
        )
      )

      val expectedProductions = List(
        ProductionEvent(
          label = "London : Wellcome Collection Publishing",
          places = List(Place("London")),
          agents = List(Agent("Wellcome Collection Publishing")),
          dates = List(),
          function = Some(Concept("Manufacture"))
        )
      )

      transformToProduction(varFields) shouldBe expectedProductions
    }

    it("picks up multiple instances of the 264 field") {
      val varFields = List(
        createVarFieldWith(
          marcTag = "264",
          indicator2 = "1",
          subfields = List(
            Subfield(tag = "a", content = "Columbia, S.C. :"),
            Subfield(tag = "b", content = "H.W. Williams Co.,"),
            Subfield(tag = "c", content = "1982")
          )
        ),
        createVarFieldWith(
          marcTag = "264",
          indicator2 = "2",
          subfields = List(
            Subfield(tag = "a", content = "Washington :"),
            Subfield(tag = "b", content = "U.S. G.P.O.,"),
            Subfield(tag = "c", content = "1981-")
          )
        )
      )

      val expectedProductions = List(
        ProductionEvent(
          label = "Columbia, S.C. : H.W. Williams Co., 1982",
          places = List(Place("Columbia, S.C.")),
          agents = List(Agent("H.W. Williams Co.")),
          dates = List(ParsedPeriod("1982")),
          function = Some(Concept("Publication"))
        ),
        ProductionEvent(
          label = "Washington : U.S. G.P.O., 1981-",
          places = List(Place("Washington")),
          agents = List(Agent("U.S. G.P.O.")),
          dates = List(ParsedPeriod("1981-")),
          function = Some(Concept("Distribution"))
        )
      )

      transformToProduction(varFields) shouldBe expectedProductions
    }

    it("normalises Place and Period labels") {
      val production = transform264ToProduction(
        subfields = List(
          Subfield(tag = "a", content = "Boston:"),
          Subfield(tag = "a", content = "Cambridge : "),
          Subfield(tag = "b", content = "ABC Publishers,"),
          Subfield(tag = "b", content = "Iverson Ltd. , "),
          Subfield(tag = "c", content = "2002."),
          Subfield(tag = "c", content = "1983 ."),
          Subfield(tag = "c", content = "copyright 2005.")
        ))

      production.places shouldBe List(
        Place(label = "Boston"),
        Place(label = "Cambridge")
      )
      production.agents shouldBe List(
        Agent(label = "ABC Publishers"),
        Agent(label = "Iverson Ltd.")
      )
      production.dates shouldBe List(
        ParsedPeriod(label = "2002"),
        ParsedPeriod(label = "1983"),
        ParsedPeriod(label = "copyright 2005")
      )
    }
  }

  describe("Both MARC field 260 and 264") {
    it("throws a cataloguing error if both 260 and 264 are present") {
      val varFields = List(
        VarField(
          marcTag = "260",
          subfields = List(
            Subfield(tag = "a", content = "Paris")
          )
        ),
        VarField(
          marcTag = "264",
          subfields = List(
            Subfield(tag = "a", content = "London")
          )
        )
      )

      val bibData = createSierraBibDataWith(varFields = varFields)
      val bibId = createSierraBibNumber

      val caught = intercept[CataloguingException] {
        SierraProduction(bibId, bibData)
      }

      caught.getMessage should startWith("Problem in the Sierra data")
      caught.getMessage should include(bibId.withoutCheckDigit)
      caught.getMessage should include("Record has both 260 and 264 fields")
    }

    it("uses 260 if 264 only contains a copyright statement in subfield c") {
      val varFields = List(
        VarField(
          marcTag = "260",
          subfields = List(
            Subfield(tag = "a", content = "San Francisco :"),
            Subfield(tag = "b", content = "Morgan Kaufmann Publishers,"),
            Subfield(tag = "c", content = "2004")
          )
        ),
        VarField(
          marcTag = "264",
          subfields = List(
            Subfield(tag = "c", content = "©2004")
          )
        )
      )

      val expectedProductions = List(
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

      transformToProduction(varFields) shouldBe expectedProductions
    }

    it("returns correctly if 260 and 264 contain the same subfields") {
      val subfields = List(
        Subfield(tag = "a", content = "London :"),
        Subfield(tag = "b", content = "Wellcome Trust,"),
        Subfield(tag = "c", content = "1992")
      )

      val varFields = List(
        VarField(
          marcTag = "260",
          subfields = subfields
        ),
        VarField(
          marcTag = "264",
          subfields = subfields
        )
      )

      val expectedProductions = List(
        ProductionEvent(
          label = "London : Wellcome Trust, 1992",
          places = List(Place("London")),
          agents = List(Agent("Wellcome Trust")),
          dates = List(ParsedPeriod("1992")),
          function = None
        )
      )

      transformToProduction(varFields) shouldBe expectedProductions
    }

    // Based on b31500018, as retrieved on 28 March 2019
    it("returns correctly if the 264 subfields only contain punctuation") {
      val varFields = List(
        VarField(
          marcTag = "260",
          subfields = List(
            Subfield(tag = "c", content = "2019")
          )
        ),
        VarField(
          marcTag = "264",
          subfields = List(
            Subfield(tag = "a", content = ":"),
            Subfield(tag = "b", content = ","),
            Subfield(tag = "c", content = "")
          )
        )
      )

      transformToProduction(varFields) shouldBe List(
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

    val varField = createVarFieldWith(
      marcTag = "008",
      content = Some("790922s1757    enk||||      o00||||eng ccam   ")
    )

    it("uses field 008 if neither 260 or 264 are present") {

      transformToProduction(List(varField)) shouldBe List(
        ProductionEvent(
          label = "1757",
          places = List(Place("England")),
          agents = List(),
          dates = List(ParsedPeriod("1757")),
          function = None))
    }

    it("ignores field 008 if 264 is present") {

      val varFields = List(
        varField,
        createVarFieldWith(
          marcTag = "264",
          indicator2 = "1",
          subfields = List(
            Subfield(tag = "c", content = "2002"),
            Subfield(tag = "a", content = "London"))))

      transformToProduction(varFields) shouldBe List(
        ProductionEvent(
          label = "2002 London",
          places = List(Place("London")),
          agents = List(),
          dates = List(ParsedPeriod("2002")),
          function = Some(Concept("Publication"))))
    }

    it("ignores field 008 if 260 is present") {

      val varFields = List(
        varField,
        createVarFieldWith(
          marcTag = "260",
          indicator2 = "1",
          subfields = List(
            Subfield(tag = "c", content = "2002"),
            Subfield(tag = "a", content = "London"))))

      transformToProduction(varFields) shouldBe List(
        ProductionEvent(
          label = "2002 London",
          places = List(Place("London")),
          agents = List(),
          dates = List(ParsedPeriod("2002")),
          function = None))
    }

    it("uses date information from 008 if not present in 260/264") {

      val varFields = List(
        varField,
        createVarFieldWith(
          marcTag = "260",
          indicator2 = "1",
          subfields = List(Subfield(tag = "a", content = "London"))))

      transformToProduction(varFields) shouldBe List(
        ProductionEvent(
          label = "London",
          places = List(Place("London")),
          agents = List(),
          dates = List(ParsedPeriod("1757")),
          function = None))
    }

    // Example is from b28533306, with the 264 field modified so it's never parseable
    // and so this test is predictable.
    it(
      "uses date information from 008 but labels from 260/4 if the latter cannot be parsed") {
      val varFields = List(
        createVarFieldWith(
          marcTag = "008",
          content = Some("160323s1972    enk               ku    d")
        ),
        createVarFieldWith(
          marcTag = "264",
          indicator2 = "0",
          subfields = List(
            Subfield(tag = "a", content = "[Netherne, Surrey],"),
            Subfield(
              tag = "c",
              content = "B̷A̴D̸ ̴U̶N̸P̵A̸R̸S̷E̷A̶B̵L̶E̸ ̵N̴O̴N̶S̵E̷N̷S̴E̴")
          )
        )
      )

      val result = transformToProduction(varFields)
      result should have length 1
      result.head shouldBe ProductionEvent(
        label =
          "[Netherne, Surrey], B̷A̴D̸ ̴U̶N̸P̵A̸R̸S̷E̷A̶B̵L̶E̸ ̵N̴O̴N̶S̵E̷N̷S̴E̴",
        places = List(Place("[Netherne, Surrey],")),
        agents = Nil,
        dates = List(
          ParsedPeriod("1972").copy(
            label = "B̷A̴D̸ ̴U̶N̸P̵A̸R̸S̷E̷A̶B̵L̶E̸ ̵N̴O̴N̶S̵E̷N̷S̴E̴")),
        function = Some(Concept("Production"))
      )
    }
  }

  // Test helpers

  private def transform260ToProduction(subfields: List[Subfield]) = {
    val varFields = List(
      VarField(
        marcTag = "260",
        subfields = subfields
      )
    )

    transformToProduction(varFields = varFields).head
  }

  private def transform264ToProduction(subfields: List[Subfield]) = {
    val varFields = List(
      createVarFieldWith(
        marcTag = "264",
        indicator2 = "1",
        subfields = subfields
      )
    )

    transformToProduction(varFields = varFields).head
  }

  private def checkProductionFunctionFor264(indicator2: String,
                                            expectedFunction: String) = {
    val varFields = List(
      createVarFieldWith(
        marcTag = "264",
        indicator2 = indicator2,
        subfields = List()
      )
    )

    val production = transformToProduction(varFields = varFields).head
    production.function shouldBe Some(Concept(expectedFunction))
  }

  private def transformToProduction(
    varFields: List[VarField]): List[ProductionEvent[IdState.Unminted]] = {
    val bibData = createSierraBibDataWith(varFields = varFields)
    SierraProduction(createSierraBibNumber, bibData)
  }
}
