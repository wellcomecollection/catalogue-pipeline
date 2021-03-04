package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.platform.transformer.sierra.generators.MarcGenerators
import uk.ac.wellcome.platform.transformer.sierra.source.{MarcSubfield, VarField}
import weco.catalogue.sierra_adapter.generators.SierraGenerators

class SierraHoldingsEnumerationTest extends AnyFunSpec with Matchers with MarcGenerators with SierraGenerators {
  it("returns an empty list if there are no varFields with 853/863") {
    val varFields = List(
      createVarFieldWith(marcTag = "866"),
      createVarFieldWith(marcTag = "989"),
    )

    getEnumerations(varFields) shouldBe List()
  }

  describe("handles the examples from the Sierra documentation") {
    // As part of adding holdings records to the Catalogue, I got a document titled
    // "Storing holdings data in 85X/86X pairs".  It featured a couple of examples
    // for holdings displays, which I reproduce here.

    it("handles a single 853/863 pair") {
      val varFields = List(
        createVarFieldWith(
          marcTag = "853",
          subfields = List(
            MarcSubfield(tag = "8", content = "10"),
            MarcSubfield(tag = "a", content = "vol."),
            MarcSubfield(tag = "i", content = "(year)")
          )
        ),
        createVarFieldWith(
          marcTag = "863",
          subfields = List(
            MarcSubfield(tag = "8", content = "10.1"),
            MarcSubfield(tag = "a", content = "1"),
            MarcSubfield(tag = "i", content = "1995")
          )
        )
      )

      getEnumerations(varFields) shouldBe List("vol.1 (1995)")
    }
  }

  it("handles an 853/863 pair that features a range with start/end") {
    // This is based on b13488557 / h10310770
    val varFields = List(
      createVarFieldWith(
        marcTag = "863",
        subfields = List(
          MarcSubfield(tag = "8", content = "1.1"),
          MarcSubfield(tag = "a", content = "1-35"),
          MarcSubfield(tag = "b", content = "1-2"),
          MarcSubfield(tag = "i", content = "1984-2018"),
        )
      ),
      createVarFieldWith(
        marcTag = "853",
        subfields = List(
          MarcSubfield(tag = "8", content = "1"),
          MarcSubfield(tag = "a", content = "v."),
          MarcSubfield(tag = "b", content = "no."),
          MarcSubfield(tag = "i", content = "(year)"),
        )
      )
    )

    getEnumerations(varFields) shouldBe List("v.1:no.1 (1984) - v.35:no.2 (2018)")
  }

  it("handles a duplicated field") {
    val varFields = List(
      createVarFieldWith(
        marcTag = "853",
        subfields = List(
          MarcSubfield(tag = "8", content = "10"),
          MarcSubfield(tag = "a", content = "vol."),
          MarcSubfield(tag = "i", content = "(year)")
        )
      ),
      createVarFieldWith(
        marcTag = "853",
        subfields = List(
          MarcSubfield(tag = "8", content = "10"),
          MarcSubfield(tag = "a", content = "vol."),
          MarcSubfield(tag = "i", content = "(year)")
        )
      ),
      createVarFieldWith(
        marcTag = "863",
        subfields = List(
          MarcSubfield(tag = "8", content = "10.1"),
          MarcSubfield(tag = "a", content = "1"),
          MarcSubfield(tag = "i", content = "2001")
        )
      )
    )

    getEnumerations(varFields) shouldBe List("vol.1 (2001)")
  }

  it("skips empty values in field 863") {
    // This is based on b13108608
    val varFields = List(
      createVarFieldWith(
        marcTag = "863",
        subfields = List(
          MarcSubfield(tag = "8", content = "1.1"),
          MarcSubfield(tag = "a", content = ""),
          MarcSubfield(tag = "b", content = "1-101"),
          MarcSubfield(tag = "i", content = "1982-2010")
        )
      ),
      createVarFieldWith(
        marcTag = "853",
        subfields = List(
          MarcSubfield(tag = "8", content = "1"),
          MarcSubfield(tag = "a", content = "v."),
          MarcSubfield(tag = "b", content = "no."),
          MarcSubfield(tag = "i", content = "(year)")
        )
      )
    )

    getEnumerations(varFields) shouldBe List("no.1 (1982) - no.101 (2010)")
  }

  it("skips empty values at one end of a range in field 863") {
    // This test case is based on b13107884
    val varFields = List(
      createVarFieldWith(
        marcTag = "863",
        subfields = List(
          MarcSubfield(tag = "8", content = "1.1"),
          MarcSubfield(tag = "a", content = "1-130"),
          MarcSubfield(tag = "b", content = "-1"),
          MarcSubfield(tag = "i", content = "1979-2010")
        )
      ),
      createVarFieldWith(
        marcTag = "853",
        subfields = List(
          MarcSubfield(tag = "8", content = "1"),
          MarcSubfield(tag = "a", content = "v."),
          MarcSubfield(tag = "b", content = "no."),
          MarcSubfield(tag = "i", content = "(year)")
        )
      )
    )

    getEnumerations(varFields) shouldBe List("v.1 (1979) - v.130:no.1 (2010)")
  }

  it("skips empty values at both ends of a range in field 863") {
    // This test case is based on b13108487
    val varFields = List(
      createVarFieldWith(
        marcTag = "863",
        subfields = List(
          MarcSubfield(tag = "8", content = "1.1"),
          MarcSubfield(tag = "a", content = "-"),
          MarcSubfield(tag = "b", content = "1-21"),
          MarcSubfield(tag = "i", content = "1984-2004")
        )
      ),
      createVarFieldWith(
        marcTag = "853",
        subfields = List(
          MarcSubfield(tag = "8", content = "1"),
          MarcSubfield(tag = "a", content = "v."),
          MarcSubfield(tag = "b", content = "no."),
          MarcSubfield(tag = "i", content = "(year)")
        )
      )
    )

    getEnumerations(varFields) shouldBe List("no.1 (1984) - no.21 (2004)")
  }

  it("handles ranges that contain multiple parts") {
    // This test case is based on b16734567
    val varFields = List(
      createVarFieldWith(
        marcTag = "863",
        subfields = List(
          MarcSubfield(tag = "8", content = "1.1"),
          MarcSubfield(tag = "a", content = "12-21"),
          MarcSubfield(tag = "b", content = "1-1-2"),
          MarcSubfield(tag = "i", content = "2009-2018")
        )
      ),
      createVarFieldWith(
        marcTag = "853",
        subfields = List(
          MarcSubfield(tag = "8", content = "1"),
          MarcSubfield(tag = "a", content = "v."),
          MarcSubfield(tag = "b", content = "no."),
          MarcSubfield(tag = "i", content = "(year)")
        )
      )
    )

    getEnumerations(varFields) shouldBe List("v.12:no.1 (2009) - v.21:no.1-2 (2018)")
  }

  it("removes parentheses from a single date") {
    val varFields = List(
      createVarFieldWith(
        marcTag = "863",
        subfields = List(
          MarcSubfield(tag = "8", content = "1.1"),
          MarcSubfield(tag = "i", content = "2010-2020")
        )
      ),
      createVarFieldWith(
        marcTag = "853",
        subfields = List(
          MarcSubfield(tag = "8", content = "1"),
          MarcSubfield(tag = "i", content = "(year)")
        )
      )
    )

    getEnumerations(varFields) shouldBe List("2010 - 2020")
  }

  it("maps numeric Season values to names") {
    // This test case is based on b15268688
    val varFields = List(
      createVarFieldWith(
        marcTag = "863",
        subfields = List(
          MarcSubfield(tag = "8", content = "1.1"),
          MarcSubfield(tag = "a", content = "41-57"),
          MarcSubfield(tag = "b", content = "4-2"),
          MarcSubfield(tag = "i", content = "1992-2008"),
          MarcSubfield(tag = "j", content = "23-21"),
        )
      ),
      createVarFieldWith(
        marcTag = "863",
        subfields = List(
          MarcSubfield(tag = "8", content = "1.2"),
          MarcSubfield(tag = "a", content = "57-59"),
          MarcSubfield(tag = "b", content = "4-1"),
          MarcSubfield(tag = "i", content = "2008-2009"),
          MarcSubfield(tag = "j", content = "23-24"),
        )
      ),
      createVarFieldWith(
        marcTag = "863",
        subfields = List(
          MarcSubfield(tag = "8", content = "1.4"),
          MarcSubfield(tag = "a", content = "60-61"),
          MarcSubfield(tag = "b", content = "3-2"),
          MarcSubfield(tag = "i", content = "2011-2012"),
          MarcSubfield(tag = "j", content = "22-21"),
        )
      ),
      createVarFieldWith(
        marcTag = "853",
        subfields = List(
          MarcSubfield(tag = "8", content = "1"),
          MarcSubfield(tag = "a", content = "v."),
          MarcSubfield(tag = "b", content = "no."),
          MarcSubfield(tag = "i", content = "(year)"),
          MarcSubfield(tag = "j", content = "(season)"),
        )
      )
    )

    getEnumerations(varFields) shouldBe List(
      "v.41:no.4 (Autumn 1992) - v.57:no.2 (Spring 2008)",
      "v.57:no.4 (Autumn 2008) - v.59:no.1 (Winter 2009)",
      "v.60:no.3 (Summer 2011) - v.61:no.2 (Spring 2012)"
    )
  }

  it("maps numeric Season values to names in the month field") {
    // This test case is based on b24968912
    val varFields = List(
      createVarFieldWith(
        marcTag = "863",
        subfields = List(
          MarcSubfield(tag = "8", content = "1.1"),
          MarcSubfield(tag = "a", content = "1-3"),
          MarcSubfield(tag = "b", content = "1-2"),
          MarcSubfield(tag = "i", content = "2015-2017"),
          MarcSubfield(tag = "j", content = "21-22"),
        )
      ),
      createVarFieldWith(
        marcTag = "853",
        subfields = List(
          MarcSubfield(tag = "8", content = "1"),
          MarcSubfield(tag = "a", content = "v."),
          MarcSubfield(tag = "b", content = "no."),
          MarcSubfield(tag = "i", content = "(year)"),
          MarcSubfield(tag = "j", content = "(month)"),
        )
      )
    )

    getEnumerations(varFields) shouldBe List(
      "v.1:no.1 (Spring 2015) - v.3:no.2 (Summer 2017)"
    )
  }

  it("maps numeric month values to names") {
    // This test case is based on b14604863
    val varFields = List(
      createVarFieldWith(
        marcTag = "863",
        subfields = List(
          MarcSubfield(tag = "8", content = "1.1"),
          MarcSubfield(tag = "a", content = "7-18"),
          MarcSubfield(tag = "b", content = "1-2"),
          MarcSubfield(tag = "i", content = "2000-2011"),
          MarcSubfield(tag = "j", content = "04-08"),
        )
      ),
      createVarFieldWith(
        marcTag = "853",
        subfields = List(
          MarcSubfield(tag = "8", content = "1"),
          MarcSubfield(tag = "a", content = "v."),
          MarcSubfield(tag = "b", content = "no."),
          MarcSubfield(tag = "i", content = "(year)"),
          MarcSubfield(tag = "j", content = "(month)"),
        )
      )
    )

    getEnumerations(varFields) shouldBe List(
      "v.7:no.1 (Apr. 2000) - v.18:no.2 (Aug. 2011)"
    )
  }

  it("handles slashes in the season field") {
    // This test case is based on b3186692x
    val varFields = List(
      createVarFieldWith(
        marcTag = "863",
        subfields = List(
          MarcSubfield(tag = "8", content = "1.1"),
          MarcSubfield(tag = "a", content = ""),
          MarcSubfield(tag = "b", content = "1-4"),
          MarcSubfield(tag = "i", content = "2017-2019"),
          MarcSubfield(tag = "j", content = "-21/22"),
        )
      ),
      createVarFieldWith(
        marcTag = "853",
        subfields = List(
          MarcSubfield(tag = "8", content = "1"),
          MarcSubfield(tag = "a", content = "v."),
          MarcSubfield(tag = "b", content = "no."),
          MarcSubfield(tag = "i", content = "(year)"),
          MarcSubfield(tag = "j", content = "(month)"),
        )
      )
    )

    getEnumerations(varFields) shouldBe List(
      "no.1 (2017) - no.4 (Spring/Summer 2019)"
    )
  }

  it("handles a mix of month/season in the same record") {
    // This test case is based on b13544019
    val varFields = List(
      createVarFieldWith(
        marcTag = "863",
        subfields = List(
          MarcSubfield(tag = "8", content = "1.1"),
          MarcSubfield(tag = "a", content = "26-47"),
          MarcSubfield(tag = "b", content = "-2"),
          MarcSubfield(tag = "i", content = "1996-2017"),
          MarcSubfield(tag = "j", content = "24-05"),
        )
      ),
      createVarFieldWith(
        marcTag = "853",
        subfields = List(
          MarcSubfield(tag = "8", content = "1"),
          MarcSubfield(tag = "a", content = "v."),
          MarcSubfield(tag = "b", content = "no."),
          MarcSubfield(tag = "i", content = "(year)"),
          MarcSubfield(tag = "j", content = "(month)"),
        )
      )
    )

    getEnumerations(varFields) shouldBe List(
      "v.26 (Winter 1996) - v.47:no.2 (May 2017)"
    )
  }

  it("includes the contents of the public note in subfield ǂz") {
    // This test case is based on b14975993
    val varFields = List(
      createVarFieldWith(
        marcTag = "863",
        subfields = List(
          MarcSubfield(tag = "8", content = "1.1"),
          MarcSubfield(tag = "a", content = "1-2"),
          MarcSubfield(tag = "b", content = "1-2"),
          MarcSubfield(tag = "z", content = "Current issue on display")
        )
      ),
      createVarFieldWith(
        marcTag = "853",
        subfields = List(
          MarcSubfield(tag = "8", content = "1"),
          MarcSubfield(tag = "a", content = "v."),
          MarcSubfield(tag = "b", content = "no."),
        )
      )
    )

    getEnumerations(varFields) shouldBe List("v.1:no.1 - v.2:no.2 Current issue on display")
  }

  describe("handles malformed MARC data") {
    it("skips a field 863 if it has a missing sequence number") {
      val varFields = List(
        createVarFieldWith(
          marcTag = "853",
          subfields = List(
            MarcSubfield(tag = "8", content = "10"),
            MarcSubfield(tag = "a", content = "vol."),
            MarcSubfield(tag = "i", content = "(year)")
          )
        ),
        createVarFieldWith(
          marcTag = "863",
          subfields = List(
            MarcSubfield(tag = "8", content = "10.1"),
            MarcSubfield(tag = "a", content = "1"),
            MarcSubfield(tag = "i", content = "1995")
          )
        ),
        createVarFieldWith(
          marcTag = "863",
          subfields = List(
            MarcSubfield(tag = "8", content = "2.1"),
            MarcSubfield(tag = "a", content = "1"),
            MarcSubfield(tag = "i", content = "1995")
          )
        )
      )

      getEnumerations(varFields) shouldBe List("vol.1 (1995)")
    }

    it("skips a subfield in field 863 if it doesn't have a corresponding label") {
      val varFields = List(
        createVarFieldWith(
          marcTag = "853",
          subfields = List(
            MarcSubfield(tag = "8", content = "10"),
            MarcSubfield(tag = "a", content = "vol."),
          )
        ),
        createVarFieldWith(
          marcTag = "863",
          subfields = List(
            MarcSubfield(tag = "8", content = "10.1"),
            MarcSubfield(tag = "a", content = "1"),
            MarcSubfield(tag = "i", content = "1995")
          )
        )
      )

      getEnumerations(varFields) shouldBe List("vol.1")
    }

    it("skips a field 863 if it can't parse the link/sequence as two integers") {
      val varFields = List(
        createVarFieldWith(
          marcTag = "853",
          subfields = List(
            MarcSubfield(tag = "8", content = "10"),
            MarcSubfield(tag = "a", content = "vol."),
            MarcSubfield(tag = "i", content = "(year)")
          )
        ),
        createVarFieldWith(
          marcTag = "863",
          subfields = List(
            MarcSubfield(tag = "8", content = "10.1"),
            MarcSubfield(tag = "a", content = "1"),
            MarcSubfield(tag = "i", content = "2001")
          )
        ),
        createVarFieldWith(
          marcTag = "863",
          subfields = List(
            MarcSubfield(tag = "8", content = "b.b"),
            MarcSubfield(tag = "a", content = "2"),
            MarcSubfield(tag = "i", content = "2002")
          )
        ),
        createVarFieldWith(
          marcTag = "863",
          subfields = List(
            MarcSubfield(tag = "8", content = "3.3.3"),
            MarcSubfield(tag = "a", content = "3"),
            MarcSubfield(tag = "i", content = "2003")
          )
        ),
        createVarFieldWith(
          marcTag = "863",
          subfields = List(
            MarcSubfield(tag = "a", content = "4"),
            MarcSubfield(tag = "i", content = "2004")
          )
        ),
      )

      getEnumerations(varFields) shouldBe List("vol.1 (2001)")
    }

    it("skips a field 853 if it can't find a sequence number") {
      val varFields = List(
        createVarFieldWith(
          marcTag = "853",
          subfields = List(
            MarcSubfield(tag = "8", content = "10"),
            MarcSubfield(tag = "a", content = "vol."),
            MarcSubfield(tag = "i", content = "(year)")
          )
        ),
        createVarFieldWith(
          marcTag = "853",
          subfields = List(
            MarcSubfield(tag = "8", content = "a"),
            MarcSubfield(tag = "a", content = "vol."),
            MarcSubfield(tag = "i", content = "(year)")
          )
        ),
        createVarFieldWith(
          marcTag = "853",
          subfields = List(
            MarcSubfield(tag = "a", content = "vol."),
            MarcSubfield(tag = "i", content = "(year)")
          )
        ),
        createVarFieldWith(
          marcTag = "863",
          subfields = List(
            MarcSubfield(tag = "8", content = "10.1"),
            MarcSubfield(tag = "a", content = "1"),
            MarcSubfield(tag = "i", content = "1995")
          )
        ),
      )

      getEnumerations(varFields) shouldBe List("vol.1 (1995)")
    }
  }

  def getEnumerations(varFields: List[VarField]): List[String] =
    SierraHoldingsEnumeration(createSierraHoldingsNumber, varFields)
}
