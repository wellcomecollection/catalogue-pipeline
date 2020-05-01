package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.sierra.exceptions.CataloguingException
import uk.ac.wellcome.platform.transformer.sierra.source.{
  MarcSubfield,
  VarField
}
import uk.ac.wellcome.platform.transformer.sierra.generators.{
  MarcGenerators,
  SierraDataGenerators
}

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
          MarcSubfield(tag = "a", content = "Paris"),
          MarcSubfield(tag = "a", content = "London")
        ))

      production.places shouldBe List(
        Place(label = "Paris"),
        Place(label = "London")
      )
    }

    it("populates agents from subfield b") {
      val production = transform260ToProduction(
        subfields = List(
          MarcSubfield(tag = "b", content = "Gauthier-Villars ;"),
          MarcSubfield(tag = "b", content = "Vogue")
        ))

      production.agents shouldBe List(
        Agent(label = "Gauthier-Villars ;"),
        Agent(label = "Vogue")
      )
    }

    it("populates dates from subfield c") {
      val production = transform260ToProduction(
        subfields = List(
          MarcSubfield(tag = "c", content = "1955"),
          MarcSubfield(tag = "c", content = "1984"),
          MarcSubfield(tag = "c", content = "1999")
        ))

      production.dates shouldBe List(
        Period(label = "1955"),
        Period(label = "1984"),
        Period(label = "1999")
      )
    }

    it("sets the function as None if it only has subfields a/b/c") {
      val production = transform260ToProduction(
        subfields = List(
          MarcSubfield(tag = "a", content = "New York"),
          MarcSubfield(tag = "b", content = "Xerox Films"),
          MarcSubfield(tag = "c", content = "1973")
        ))

      production.function shouldBe None
    }

    it("populates places from a and e, and sets the function as Manufacture") {
      val production = transform260ToProduction(
        subfields = List(
          MarcSubfield(tag = "a", content = "New York, N.Y."),
          MarcSubfield(tag = "e", content = "Reston, Va."),
          MarcSubfield(tag = "e", content = "[Philadelphia]"),
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
          MarcSubfield(tag = "b", content = "Macmillan"),
          MarcSubfield(tag = "f", content = "Sussex Tapes"),
          MarcSubfield(tag = "f", content = "US Dept of Energy"),
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
          MarcSubfield(tag = "c", content = "1981"),
          MarcSubfield(tag = "g", content = "April 15, 1977"),
          MarcSubfield(tag = "g", content = "1973 printing")
        ))

      production.dates shouldBe List(
        Period(label = "1981"),
        Period(label = "April 15, 1977"),
        Period(label = "1973 printing")
      )

      production.function shouldBe Some(Concept("Manufacture"))
    }

    it("picks up multiple instances of the 260 field") {
      val varFields = List(
        createVarFieldWith(
          marcTag = "260",
          subfields = List(
            MarcSubfield(tag = "a", content = "London :"),
            MarcSubfield(tag = "b", content = "Arts Council of Great Britain,"),
            MarcSubfield(tag = "c", content = "1976;"),
            MarcSubfield(tag = "e", content = "Twickenham :"),
            MarcSubfield(tag = "f", content = "CTD Printers,"),
            MarcSubfield(tag = "g", content = "1974")
          )
        ),
        createVarFieldWith(
          marcTag = "260",
          subfields = List(
            MarcSubfield(tag = "a", content = "Bethesda, Md. :"),
            MarcSubfield(
              tag = "b",
              content =
                "Toxicology Information Program, National Library of Medicine [producer] ;"),
            MarcSubfield(tag = "a", content = "Springfield, Va. :"),
            MarcSubfield(
              tag = "b",
              content = "National Technical Information Service [distributor],"),
            MarcSubfield(tag = "c", content = "1974-")
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
          dates = List(Period("1976;"), Period("1974")),
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
          dates = List(Period("1974-")),
          function = None
        )
      )

      transformToProduction(varFields) shouldBe expectedProductions
    }

    it("normalises Place and Period labels") {
      val production = transform260ToProduction(
        subfields = List(
          MarcSubfield(tag = "a", content = "Paris  : "),
          MarcSubfield(tag = "a", content = "London :"),
          MarcSubfield(tag = "c", content = "1984 . "),
          MarcSubfield(tag = "c", content = "1999.")
        ))

      production.places shouldBe List(
        Place(label = "Paris"),
        Place(label = "London")
      )

      production.dates shouldBe List(
        Period(label = "1984"),
        Period(label = "1999")
      )
    }
  }

  // Examples are taken from the MARC spec for field 264.
  // https://www.loc.gov/marc/bibliographic/bd264.html

  describe("MARC field 264") {
    it("populates places from subfield a") {
      val production = transform264ToProduction(
        subfields = List(
          MarcSubfield(tag = "a", content = "Boston"),
          MarcSubfield(tag = "a", content = "Cambridge")
        ))

      production.places shouldBe List(
        Place(label = "Boston"),
        Place(label = "Cambridge")
      )
    }

    it("populates agents from subfield b") {
      val production = transform264ToProduction(
        subfields = List(
          MarcSubfield(tag = "b", content = "ABC Publishers"),
          MarcSubfield(tag = "b", content = "Iverson Company")
        ))

      production.agents shouldBe List(
        Agent(label = "ABC Publishers"),
        Agent(label = "Iverson Company")
      )
    }

    it("populates dates from subfield c") {
      val production = transform264ToProduction(
        subfields = List(
          MarcSubfield(tag = "c", content = "2002"),
          MarcSubfield(tag = "c", content = "1983"),
          MarcSubfield(tag = "c", content = "copyright 2005")
        ))

      production.dates shouldBe List(
        Period(label = "2002"),
        Period(label = "1983"),
        Period(label = "copyright 2005")
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
            MarcSubfield(tag = "c", content = "copyright 2005")
          )
        ),
        createVarFieldWith(
          marcTag = "264",
          indicator2 = "3",
          subfields = List(
            MarcSubfield(tag = "a", content = "Cambridge :"),
            MarcSubfield(tag = "b", content = "Kinsey Printing Company")
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
            MarcSubfield(tag = "c", content = "copyright 2005")
          )
        ),
        createVarFieldWith(
          marcTag = "264",
          indicator2 = "3",
          subfields = List(
            MarcSubfield(tag = "a", content = "London :"),
            MarcSubfield(tag = "b", content = "Wellcome Collection Publishing")
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
            MarcSubfield(tag = "a", content = "Columbia, S.C. :"),
            MarcSubfield(tag = "b", content = "H.W. Williams Co.,"),
            MarcSubfield(tag = "c", content = "1982")
          )
        ),
        createVarFieldWith(
          marcTag = "264",
          indicator2 = "2",
          subfields = List(
            MarcSubfield(tag = "a", content = "Washington :"),
            MarcSubfield(tag = "b", content = "U.S. G.P.O.,"),
            MarcSubfield(tag = "c", content = "1981-")
          )
        )
      )

      val expectedProductions = List(
        ProductionEvent(
          label = "Columbia, S.C. : H.W. Williams Co., 1982",
          places = List(Place("Columbia, S.C.")),
          agents = List(Agent("H.W. Williams Co.")),
          dates = List(Period("1982")),
          function = Some(Concept("Publication"))
        ),
        ProductionEvent(
          label = "Washington : U.S. G.P.O., 1981-",
          places = List(Place("Washington")),
          agents = List(Agent("U.S. G.P.O.")),
          dates = List(Period("1981-")),
          function = Some(Concept("Distribution"))
        )
      )

      transformToProduction(varFields) shouldBe expectedProductions
    }

    it("normalises Place and Period labels") {
      val production = transform264ToProduction(
        subfields = List(
          MarcSubfield(tag = "a", content = "Boston:"),
          MarcSubfield(tag = "a", content = "Cambridge : "),
          MarcSubfield(tag = "b", content = "ABC Publishers,"),
          MarcSubfield(tag = "b", content = "Iverson Ltd. , "),
          MarcSubfield(tag = "c", content = "2002."),
          MarcSubfield(tag = "c", content = "1983 ."),
          MarcSubfield(tag = "c", content = "copyright 2005.")
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
        Period(label = "2002"),
        Period(label = "1983"),
        Period(label = "copyright 2005")
      )
    }
  }

  describe("Both MARC field 260 and 264") {
    it("throws a cataloguing error if both 260 and 264 are present") {
      val varFields = List(
        createVarFieldWith(
          marcTag = "260",
          subfields = List(
            MarcSubfield(tag = "a", content = "Paris")
          )
        ),
        createVarFieldWith(
          marcTag = "264",
          subfields = List(
            MarcSubfield(tag = "a", content = "London")
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
        createVarFieldWith(
          marcTag = "260",
          subfields = List(
            MarcSubfield(tag = "a", content = "San Francisco :"),
            MarcSubfield(tag = "b", content = "Morgan Kaufmann Publishers,"),
            MarcSubfield(tag = "c", content = "2004")
          )
        ),
        createVarFieldWith(
          marcTag = "264",
          subfields = List(
            MarcSubfield(tag = "c", content = "©2004")
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
          dates = List(Period("2004")),
          function = None
        )
      )

      transformToProduction(varFields) shouldBe expectedProductions
    }

    it("returns correctly if 260 and 264 contain the same subfields") {
      val subfields = List(
        MarcSubfield(tag = "a", content = "London :"),
        MarcSubfield(tag = "b", content = "Wellcome Trust,"),
        MarcSubfield(tag = "c", content = "1992")
      )

      val varFields = List(
        createVarFieldWith(
          marcTag = "260",
          subfields = subfields
        ),
        createVarFieldWith(
          marcTag = "264",
          subfields = subfields
        )
      )

      val expectedProductions = List(
        ProductionEvent(
          label = "London : Wellcome Trust, 1992",
          places = List(Place("London")),
          agents = List(Agent("Wellcome Trust")),
          dates = List(Period("1992")),
          function = None
        )
      )

      transformToProduction(varFields) shouldBe expectedProductions
    }

    // Based on b31500018, as retrieved on 28 March 2019
    it("returns correctly if the 264 subfields only contain punctuation") {
      val varFields = List(
        createVarFieldWith(
          marcTag = "260",
          subfields = List(
            MarcSubfield(tag = "c", content = "2019")
          )
        ),
        createVarFieldWith(
          marcTag = "264",
          subfields = List(
            MarcSubfield(tag = "a", content = ":"),
            MarcSubfield(tag = "b", content = ","),
            MarcSubfield(tag = "c", content = "")
          )
        )
      )

      transformToProduction(varFields) shouldBe List(
        ProductionEvent(
          label = "2019",
          places = List(),
          agents = List(),
          dates = List(Period("2019")),
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
          dates = List(Period("1757")),
          function = None))
    }

    it("ignores field 008 if 264 is present") {

      val varFields = List(
        varField,
        createVarFieldWith(
          marcTag = "264",
          indicator2 = "1",
          subfields = List(
            MarcSubfield(tag = "c", content = "2002"),
            MarcSubfield(tag = "a", content = "London"))))

      transformToProduction(varFields) shouldBe List(
        ProductionEvent(
          label = "2002 London",
          places = List(Place("London")),
          agents = List(),
          dates = List(Period("2002")),
          function = Some(Concept("Publication"))))
    }

    it("ignores field 008 if 260 is present") {

      val varFields = List(
        varField,
        createVarFieldWith(
          marcTag = "260",
          indicator2 = "1",
          subfields = List(
            MarcSubfield(tag = "c", content = "2002"),
            MarcSubfield(tag = "a", content = "London"))))

      transformToProduction(varFields) shouldBe List(
        ProductionEvent(
          label = "2002 London",
          places = List(Place("London")),
          agents = List(),
          dates = List(Period("2002")),
          function = None))
    }

    it("uses date information from 008 if not present in 260/264") {

      val varFields = List(
        varField,
        createVarFieldWith(
          marcTag = "260",
          indicator2 = "1",
          subfields = List(MarcSubfield(tag = "a", content = "London"))))

      transformToProduction(varFields) shouldBe List(
        ProductionEvent(
          label = "London",
          places = List(Place("London")),
          agents = List(),
          dates = List(Period("1757")),
          function = None))
    }
  }

  // Test helpers

  private def transform260ToProduction(subfields: List[MarcSubfield]) = {
    val varFields = List(
      createVarFieldWith(
        marcTag = "260",
        subfields = subfields
      )
    )

    transformToProduction(varFields = varFields).head
  }

  private def transform264ToProduction(subfields: List[MarcSubfield]) = {
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
    varFields: List[VarField]): List[ProductionEvent[Unminted]] = {
    val bibData = createSierraBibDataWith(varFields = varFields)
    SierraProduction(createSierraBibNumber, bibData)
  }
}
