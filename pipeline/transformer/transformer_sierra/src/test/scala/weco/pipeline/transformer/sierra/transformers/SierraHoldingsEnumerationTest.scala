package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.source_model.generators.SierraRecordGenerators
import weco.sierra.generators.MarcGenerators
import weco.sierra.models.marc.{Subfield, VarField}

class SierraHoldingsEnumerationTest
    extends AnyFunSpec
    with Matchers
    with MarcGenerators
    with SierraRecordGenerators {
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
        VarField(
          marcTag = "853",
          subfields = List(
            Subfield(tag = "8", content = "10"),
            Subfield(tag = "a", content = "vol."),
            Subfield(tag = "i", content = "(year)")
          )
        ),
        VarField(
          marcTag = "863",
          subfields = List(
            Subfield(tag = "8", content = "10.1"),
            Subfield(tag = "a", content = "1"),
            Subfield(tag = "i", content = "1995")
          )
        )
      )

      getEnumerations(varFields) shouldBe List("vol.1 (1995)")
    }
  }

  it("handles an 853/863 pair that features a range with start/end") {
    // This is based on b13488557 / h10310770
    val varFields = List(
      VarField(
        marcTag = "863",
        subfields = List(
          Subfield(tag = "8", content = "1.1"),
          Subfield(tag = "a", content = "1-35"),
          Subfield(tag = "b", content = "1-2"),
          Subfield(tag = "i", content = "1984-2018"),
        )
      ),
      VarField(
        marcTag = "853",
        subfields = List(
          Subfield(tag = "8", content = "1"),
          Subfield(tag = "a", content = "v."),
          Subfield(tag = "b", content = "no."),
          Subfield(tag = "i", content = "(year)"),
        )
      )
    )

    getEnumerations(varFields) shouldBe List(
      "v.1:no.1 (1984) - v.35:no.2 (2018)")
  }

  it("handles a duplicated field") {
    val varFields = List(
      VarField(
        marcTag = "853",
        subfields = List(
          Subfield(tag = "8", content = "10"),
          Subfield(tag = "a", content = "vol."),
          Subfield(tag = "i", content = "(year)")
        )
      ),
      VarField(
        marcTag = "853",
        subfields = List(
          Subfield(tag = "8", content = "10"),
          Subfield(tag = "a", content = "vol."),
          Subfield(tag = "i", content = "(year)")
        )
      ),
      VarField(
        marcTag = "863",
        subfields = List(
          Subfield(tag = "8", content = "10.1"),
          Subfield(tag = "a", content = "1"),
          Subfield(tag = "i", content = "2001")
        )
      )
    )

    getEnumerations(varFields) shouldBe List("vol.1 (2001)")
  }

  it("deduplicates based on the rendered values") {
    // This is based on holdings record c11058213, which is linked
    // to bib b29248164
    val varFields = List(
      VarField(
        marcTag = "863",
        subfields = List(
          Subfield(tag = "8", content = "1.1"),
          Subfield(tag = "i", content = "2004-"),
          Subfield(tag = "j", content = "01-"),
          Subfield(tag = "k", content = "01-"),
        )
      ),
      VarField(
        marcTag = "863",
        subfields = List(
          Subfield(tag = "8", content = "1.2"),
          Subfield(tag = "i", content = "2004-"),
          Subfield(tag = "j", content = "01-"),
          Subfield(tag = "k", content = "01-"),
        )
      ),
      VarField(
        marcTag = "853",
        subfields = List(
          Subfield(tag = "8", content = "1"),
          Subfield(tag = "i", content = "(year)"),
          Subfield(tag = "j", content = "(month)"),
          Subfield(tag = "k", content = "(day)"),
        )
      )
    )

    getEnumerations(varFields) shouldBe List("1 Jan. 2004 -")
  }

  it("skips empty values in field 863") {
    // This is based on b13108608
    val varFields = List(
      VarField(
        marcTag = "863",
        subfields = List(
          Subfield(tag = "8", content = "1.1"),
          Subfield(tag = "a", content = ""),
          Subfield(tag = "b", content = "1-101"),
          Subfield(tag = "i", content = "1982-2010")
        )
      ),
      VarField(
        marcTag = "853",
        subfields = List(
          Subfield(tag = "8", content = "1"),
          Subfield(tag = "a", content = "v."),
          Subfield(tag = "b", content = "no."),
          Subfield(tag = "i", content = "(year)")
        )
      )
    )

    getEnumerations(varFields) shouldBe List("no.1 (1982) - no.101 (2010)")
  }

  it("skips empty values at one end of a range in field 863") {
    // This test case is based on b13107884
    val varFields = List(
      VarField(
        marcTag = "863",
        subfields = List(
          Subfield(tag = "8", content = "1.1"),
          Subfield(tag = "a", content = "1-130"),
          Subfield(tag = "b", content = "-1"),
          Subfield(tag = "i", content = "1979-2010")
        )
      ),
      VarField(
        marcTag = "853",
        subfields = List(
          Subfield(tag = "8", content = "1"),
          Subfield(tag = "a", content = "v."),
          Subfield(tag = "b", content = "no."),
          Subfield(tag = "i", content = "(year)")
        )
      )
    )

    getEnumerations(varFields) shouldBe List("v.1 (1979) - v.130:no.1 (2010)")
  }

  it("skips empty values at both ends of a range in field 863") {
    // This test case is based on b13108487
    val varFields = List(
      VarField(
        marcTag = "863",
        subfields = List(
          Subfield(tag = "8", content = "1.1"),
          Subfield(tag = "a", content = "-"),
          Subfield(tag = "b", content = "1-21"),
          Subfield(tag = "i", content = "1984-2004")
        )
      ),
      VarField(
        marcTag = "853",
        subfields = List(
          Subfield(tag = "8", content = "1"),
          Subfield(tag = "a", content = "v."),
          Subfield(tag = "b", content = "no."),
          Subfield(tag = "i", content = "(year)")
        )
      )
    )

    getEnumerations(varFields) shouldBe List("no.1 (1984) - no.21 (2004)")
  }

  it("handles ranges that contain multiple parts") {
    // This test case is based on b16734567
    val varFields = List(
      VarField(
        marcTag = "863",
        subfields = List(
          Subfield(tag = "8", content = "1.1"),
          Subfield(tag = "a", content = "12-21"),
          Subfield(tag = "b", content = "1-1-2"),
          Subfield(tag = "i", content = "2009-2018")
        )
      ),
      VarField(
        marcTag = "853",
        subfields = List(
          Subfield(tag = "8", content = "1"),
          Subfield(tag = "a", content = "v."),
          Subfield(tag = "b", content = "no."),
          Subfield(tag = "i", content = "(year)")
        )
      )
    )

    getEnumerations(varFields) shouldBe List(
      "v.12:no.1 (2009) - v.21:no.1-2 (2018)")
  }

  it("removes parentheses from a single date") {
    val varFields = List(
      VarField(
        marcTag = "863",
        subfields = List(
          Subfield(tag = "8", content = "1.1"),
          Subfield(tag = "i", content = "2010-2020")
        )
      ),
      VarField(
        marcTag = "853",
        subfields = List(
          Subfield(tag = "8", content = "1"),
          Subfield(tag = "i", content = "(year)")
        )
      )
    )

    getEnumerations(varFields) shouldBe List("2010 - 2020")
  }

  it("maps numeric Season values to names") {
    // This test case is based on b15268688
    val varFields = List(
      VarField(
        marcTag = "863",
        subfields = List(
          Subfield(tag = "8", content = "1.1"),
          Subfield(tag = "a", content = "41-57"),
          Subfield(tag = "b", content = "4-2"),
          Subfield(tag = "i", content = "1992-2008"),
          Subfield(tag = "j", content = "23-21"),
        )
      ),
      VarField(
        marcTag = "863",
        subfields = List(
          Subfield(tag = "8", content = "1.2"),
          Subfield(tag = "a", content = "57-59"),
          Subfield(tag = "b", content = "4-1"),
          Subfield(tag = "i", content = "2008-2009"),
          Subfield(tag = "j", content = "23-24"),
        )
      ),
      VarField(
        marcTag = "863",
        subfields = List(
          Subfield(tag = "8", content = "1.4"),
          Subfield(tag = "a", content = "60-61"),
          Subfield(tag = "b", content = "3-2"),
          Subfield(tag = "i", content = "2011-2012"),
          Subfield(tag = "j", content = "22-21"),
        )
      ),
      VarField(
        marcTag = "853",
        subfields = List(
          Subfield(tag = "8", content = "1"),
          Subfield(tag = "a", content = "v."),
          Subfield(tag = "b", content = "no."),
          Subfield(tag = "i", content = "(year)"),
          Subfield(tag = "j", content = "(season)"),
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
      VarField(
        marcTag = "863",
        subfields = List(
          Subfield(tag = "8", content = "1.1"),
          Subfield(tag = "a", content = "1-3"),
          Subfield(tag = "b", content = "1-2"),
          Subfield(tag = "i", content = "2015-2017"),
          Subfield(tag = "j", content = "21-22"),
        )
      ),
      VarField(
        marcTag = "853",
        subfields = List(
          Subfield(tag = "8", content = "1"),
          Subfield(tag = "a", content = "v."),
          Subfield(tag = "b", content = "no."),
          Subfield(tag = "i", content = "(year)"),
          Subfield(tag = "j", content = "(month)"),
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
      VarField(
        marcTag = "863",
        subfields = List(
          Subfield(tag = "8", content = "1.1"),
          Subfield(tag = "a", content = "7-18"),
          Subfield(tag = "b", content = "1-2"),
          Subfield(tag = "i", content = "2000-2011"),
          Subfield(tag = "j", content = "04-08"),
        )
      ),
      VarField(
        marcTag = "853",
        subfields = List(
          Subfield(tag = "8", content = "1"),
          Subfield(tag = "a", content = "v."),
          Subfield(tag = "b", content = "no."),
          Subfield(tag = "i", content = "(year)"),
          Subfield(tag = "j", content = "(month)"),
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
      VarField(
        marcTag = "863",
        subfields = List(
          Subfield(tag = "8", content = "1.1"),
          Subfield(tag = "a", content = ""),
          Subfield(tag = "b", content = "1-4"),
          Subfield(tag = "i", content = "2017-2019"),
          Subfield(tag = "j", content = "-21/22"),
        )
      ),
      VarField(
        marcTag = "853",
        subfields = List(
          Subfield(tag = "8", content = "1"),
          Subfield(tag = "a", content = "v."),
          Subfield(tag = "b", content = "no."),
          Subfield(tag = "i", content = "(year)"),
          Subfield(tag = "j", content = "(month)"),
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
      VarField(
        marcTag = "863",
        subfields = List(
          Subfield(tag = "8", content = "1.1"),
          Subfield(tag = "a", content = "26-47"),
          Subfield(tag = "b", content = "-2"),
          Subfield(tag = "i", content = "1996-2017"),
          Subfield(tag = "j", content = "24-05"),
        )
      ),
      VarField(
        marcTag = "853",
        subfields = List(
          Subfield(tag = "8", content = "1"),
          Subfield(tag = "a", content = "v."),
          Subfield(tag = "b", content = "no."),
          Subfield(tag = "i", content = "(year)"),
          Subfield(tag = "j", content = "(month)"),
        )
      )
    )

    getEnumerations(varFields) shouldBe List(
      "v.26 (Winter 1996) - v.47:no.2 (May 2017)"
    )
  }

  it("handles a range and a slash in the month field") {
    // This example is based on b1652927
    val varFields = List(
      VarField(
        marcTag = "853",
        subfields = List(
          Subfield(tag = "8", content = "1"),
          Subfield(tag = "i", content = "(year)"),
          Subfield(tag = "j", content = "(month)")
        )
      ),
      VarField(
        marcTag = "863",
        subfields = List(
          Subfield(tag = "8", content = "1.1"),
          Subfield(tag = "i", content = "2005-2014/2015"),
          Subfield(tag = "j", content = "07-12/01")
        )
      )
    )

    // TODO: This test case is based on the current behaviour of Encore, but
    // arguably this is wrong.  The correct output should be
    //
    //    July 2005 - Dec. 2014/Jan. 2015
    //
    // but we omit fixing this for now.
    getEnumerations(varFields) shouldBe List("July 2005 - Dec./Jan. 2014/2015")
  }

  it("skips adding a value if it can't parse it as a date") {
    val varFields = List(
      VarField(
        marcTag = "853",
        subfields = List(
          Subfield(tag = "8", content = "1"),
          Subfield(tag = "i", content = "(year)"),
          Subfield(tag = "j", content = "(month)")
        )
      ),
      VarField(
        marcTag = "863",
        subfields = List(
          Subfield(tag = "8", content = "1.1"),
          Subfield(tag = "i", content = "2001-2002"),
          Subfield(tag = "j", content = "XX-YY")
        )
      )
    )

    getEnumerations(varFields) shouldBe List("2001 - 2002")
  }

  it("uses the first month of a range") {
    // This example is based on b3225790
    val varFields = List(
      VarField(
        marcTag = "853",
        subfields = List(
          Subfield(tag = "8", content = "1"),
          Subfield(tag = "i", content = "(year)"),
          Subfield(tag = "j", content = "(month)")
        )
      ),
      VarField(
        marcTag = "863",
        subfields = List(
          Subfield(tag = "8", content = "1.1"),
          Subfield(tag = "i", content = "1968-1969"),
          Subfield(tag = "j", content = "09-11-12")
        )
      )
    )

    getEnumerations(varFields) shouldBe List("Sept. 1968 - Nov. 1969")
  }

  it("includes the contents of the public note in subfield ǂz") {
    // This test case is based on b14975993
    val varFields = List(
      VarField(
        marcTag = "863",
        subfields = List(
          Subfield(tag = "8", content = "1.1"),
          Subfield(tag = "a", content = "1-2"),
          Subfield(tag = "b", content = "1-2"),
          Subfield(tag = "z", content = "Current issue on display")
        )
      ),
      VarField(
        marcTag = "853",
        subfields = List(
          Subfield(tag = "8", content = "1"),
          Subfield(tag = "a", content = "v."),
          Subfield(tag = "b", content = "no."),
        )
      )
    )

    getEnumerations(varFields) shouldBe List(
      "v.1:no.1 - v.2:no.2 Current issue on display")
  }

  it("sorts based on the link/sequence number") {
    val varFields = List(
      VarField(
        marcTag = "863",
        subfields = List(
          Subfield(tag = "8", content = "1.2"),
          Subfield(tag = "a", content = "1"),
          Subfield(tag = "b", content = "2")
        )
      ),
      VarField(
        marcTag = "863",
        subfields = List(
          Subfield(tag = "8", content = "2.1"),
          Subfield(tag = "a", content = "2"),
          Subfield(tag = "b", content = "1"),
        )
      ),
      VarField(
        marcTag = "863",
        subfields = List(
          Subfield(tag = "8", content = "1.1"),
          Subfield(tag = "a", content = "1"),
          Subfield(tag = "b", content = "1")
        )
      ),
      VarField(
        marcTag = "853",
        subfields = List(
          Subfield(tag = "8", content = "1"),
          Subfield(tag = "a", content = "v."),
          Subfield(tag = "b", content = "no."),
        )
      ),
      VarField(
        marcTag = "853",
        subfields = List(
          Subfield(tag = "8", content = "2"),
          Subfield(tag = "a", content = "v."),
          Subfield(tag = "b", content = "no."),
        )
      )
    )

    getEnumerations(varFields) shouldBe List("v.1:no.1", "v.1:no.2", "v.2:no.1")
  }

  it("uses a colon as a separator between 'v' and 'no.'") {
    // This example is based on b1310812
    val varFields = List(
      VarField(
        marcTag = "863",
        subfields = List(
          Subfield(tag = "8", content = "1.1"),
          Subfield(tag = "a", content = "42"),
          Subfield(tag = "b", content = "1-2")
        )
      ),
      VarField(
        marcTag = "853",
        subfields = List(
          Subfield(tag = "8", content = "1"),
          Subfield(tag = "a", content = "v"),
          Subfield(tag = "b", content = "no."),
        )
      )
    )

    // TODO: We might consider normalising this to 'v.' to be consistent with
    // our other holdings, but not right now -- this is meant to mimic the
    // behaviour of the old Wellcome Library site.
    getEnumerations(varFields) shouldBe List("v42:no.1 - v42:no.2")
  }

  it("trims trailing punctuation from the year") {
    // This example is based on b1310916
    val varFields = List(
      VarField(
        marcTag = "863",
        subfields = List(
          Subfield(tag = "8", content = "1.1"),
          Subfield(tag = "i", content = "1985-2002.")
        )
      ),
      VarField(
        marcTag = "853",
        subfields = List(
          Subfield(tag = "8", content = "1"),
          Subfield(tag = "i", content = "year")
        )
      )
    )

    getEnumerations(varFields) shouldBe List("1985 - 2002")
  }

  it("includes the day if the range is a month") {
    val varFields = List(
      VarField(
        marcTag = "863",
        subfields = List(
          Subfield(tag = "8", content = "1.1"),
          Subfield(tag = "i", content = "1991-2017"),
          Subfield(tag = "j", content = "02-04"),
          Subfield(tag = "k", content = "13-17"),
        )
      ),
      VarField(
        marcTag = "853",
        subfields = List(
          Subfield(tag = "8", content = "1"),
          Subfield(tag = "i", content = "(year)"),
          Subfield(tag = "j", content = "(month)"),
          Subfield(tag = "k", content = "(day)"),
        )
      )
    )

    getEnumerations(varFields) shouldBe List("13 Feb. 1991 - 17 Apr. 2017")
  }

  it("skips the day if the month value contains a season") {
    val varFields = List(
      VarField(
        marcTag = "863",
        subfields = List(
          Subfield(tag = "8", content = "1.1"),
          Subfield(tag = "i", content = "2001"),
          Subfield(tag = "j", content = "23"),
          Subfield(tag = "k", content = "1"),
        )
      ),
      VarField(
        marcTag = "853",
        subfields = List(
          Subfield(tag = "8", content = "1"),
          Subfield(tag = "i", content = "(year)"),
          Subfield(tag = "j", content = "(month)"),
          Subfield(tag = "k", content = "(day)"),
        )
      )
    )

    getEnumerations(varFields) shouldBe List("Autumn 2001")
  }

  it("strips a leading zero from the day") {
    val varFields = List(
      VarField(
        marcTag = "863",
        subfields = List(
          Subfield(tag = "8", content = "1.1"),
          Subfield(tag = "i", content = "2001"),
          Subfield(tag = "j", content = "01"),
          Subfield(tag = "k", content = "01"),
        )
      ),
      VarField(
        marcTag = "853",
        subfields = List(
          Subfield(tag = "8", content = "1"),
          Subfield(tag = "i", content = "(year)"),
          Subfield(tag = "j", content = "(month)"),
          Subfield(tag = "k", content = "(day)"),
        )
      )
    )

    getEnumerations(varFields) shouldBe List("1 Jan. 2001")
  }

  it("collapses a range which has the same start/finish") {
    // This is based on the holdings record for The Lancet.  Ideally we'd fix
    // this in the source data, but detecting this issue is somewhat fiddly
    // and it's pretty easy for us to handle here.
    val varFields = List(
      VarField(
        marcTag = "863",
        subfields = List(
          Subfield(tag = "8", content = "2.9"),
          Subfield(tag = "a", content = "390"),
          Subfield(tag = "b", content = "10116-10116"),
          Subfield(tag = "i", content = "2018"),
        )
      ),
      VarField(
        marcTag = "853",
        subfields = List(
          Subfield(tag = "8", content = "2"),
          Subfield(tag = "a", content = "v."),
          Subfield(tag = "b", content = "no."),
          Subfield(tag = "i", content = "(year)"),
        )
      )
    )

    getEnumerations(varFields) shouldBe List("v.390:no.10116 (2018)")
  }

  describe("handles malformed MARC data") {
    it("skips a field 863 if it has a missing sequence number") {
      val varFields = List(
        VarField(
          marcTag = "853",
          subfields = List(
            Subfield(tag = "8", content = "10"),
            Subfield(tag = "a", content = "vol."),
            Subfield(tag = "i", content = "(year)")
          )
        ),
        VarField(
          marcTag = "863",
          subfields = List(
            Subfield(tag = "8", content = "10.1"),
            Subfield(tag = "a", content = "1"),
            Subfield(tag = "i", content = "1995")
          )
        ),
        VarField(
          marcTag = "863",
          subfields = List(
            Subfield(tag = "8", content = "2.1"),
            Subfield(tag = "a", content = "1"),
            Subfield(tag = "i", content = "1995")
          )
        )
      )

      getEnumerations(varFields) shouldBe List("vol.1 (1995)")
    }

    it("skips a subfield in field 863 if it doesn't have a corresponding label") {
      val varFields = List(
        VarField(
          marcTag = "853",
          subfields = List(
            Subfield(tag = "8", content = "10"),
            Subfield(tag = "a", content = "vol."),
          )
        ),
        VarField(
          marcTag = "863",
          subfields = List(
            Subfield(tag = "8", content = "10.1"),
            Subfield(tag = "a", content = "1"),
            Subfield(tag = "i", content = "1995")
          )
        )
      )

      getEnumerations(varFields) shouldBe List("vol.1")
    }

    it("skips a field 863 if it can't parse the link/sequence as two integers") {
      val varFields = List(
        VarField(
          marcTag = "853",
          subfields = List(
            Subfield(tag = "8", content = "10"),
            Subfield(tag = "a", content = "vol."),
            Subfield(tag = "i", content = "(year)")
          )
        ),
        VarField(
          marcTag = "863",
          subfields = List(
            Subfield(tag = "8", content = "10.1"),
            Subfield(tag = "a", content = "1"),
            Subfield(tag = "i", content = "2001")
          )
        ),
        VarField(
          marcTag = "863",
          subfields = List(
            Subfield(tag = "8", content = "b.b"),
            Subfield(tag = "a", content = "2"),
            Subfield(tag = "i", content = "2002")
          )
        ),
        VarField(
          marcTag = "863",
          subfields = List(
            Subfield(tag = "8", content = "3.3.3"),
            Subfield(tag = "a", content = "3"),
            Subfield(tag = "i", content = "2003")
          )
        ),
        VarField(
          marcTag = "863",
          subfields = List(
            Subfield(tag = "a", content = "4"),
            Subfield(tag = "i", content = "2004")
          )
        ),
      )

      getEnumerations(varFields) shouldBe List("vol.1 (2001)")
    }

    it("skips a field 853 if it can't find a sequence number") {
      val varFields = List(
        VarField(
          marcTag = "853",
          subfields = List(
            Subfield(tag = "8", content = "10"),
            Subfield(tag = "a", content = "vol."),
            Subfield(tag = "i", content = "(year)")
          )
        ),
        VarField(
          marcTag = "853",
          subfields = List(
            Subfield(tag = "8", content = "a"),
            Subfield(tag = "a", content = "vol."),
            Subfield(tag = "i", content = "(year)")
          )
        ),
        VarField(
          marcTag = "853",
          subfields = List(
            Subfield(tag = "a", content = "vol."),
            Subfield(tag = "i", content = "(year)")
          )
        ),
        VarField(
          marcTag = "863",
          subfields = List(
            Subfield(tag = "8", content = "10.1"),
            Subfield(tag = "a", content = "1"),
            Subfield(tag = "i", content = "1995")
          )
        ),
      )

      getEnumerations(varFields) shouldBe List("vol.1 (1995)")
    }

    it("handles a duplicate 853") {
      val varFields = List(
        VarField(
          marcTag = "853",
          subfields = List(
            Subfield(tag = "8", content = "10"),
            Subfield(tag = "a", content = "vol."),
            Subfield(tag = "i", content = "(year)")
          )
        ),
        VarField(
          marcTag = "853",
          subfields = List(
            Subfield(tag = "8", content = "10"),
            Subfield(tag = "a", content = "v."),
            Subfield(tag = "i", content = "(year)")
          )
        ),
        VarField(
          marcTag = "863",
          subfields = List(
            Subfield(tag = "8", content = "10.1"),
            Subfield(tag = "a", content = "1"),
            Subfield(tag = "i", content = "2001")
          )
        )
      )

      val possibilities = List(
        List("vol.1 (2001)"),
        List("v.1 (2001)")
      )

      possibilities should contain(getEnumerations(varFields))
    }
  }

  it("finds a human-written description in field tag h") {
    val varFields = List(
      VarField(
        fieldTag = "h",
        content = "Vol. 1 (1908-1914)"
      )
    )

    getEnumerations(varFields) shouldBe List("Vol. 1 (1908-1914)")
  }

  it("puts human-written descriptions before automatic enumerations") {
    val varFields = List(
      VarField(
        fieldTag = "h",
        content = "Vol. 1 (1908-1914)"
      ),
      VarField(
        marcTag = "863",
        subfields = List(
          Subfield(tag = "8", content = "1.1"),
          Subfield(tag = "a", content = "1-35"),
          Subfield(tag = "b", content = "1-2"),
          Subfield(tag = "i", content = "1984-2018"),
        )
      ),
      VarField(
        marcTag = "853",
        subfields = List(
          Subfield(tag = "8", content = "1"),
          Subfield(tag = "a", content = "v."),
          Subfield(tag = "b", content = "no."),
          Subfield(tag = "i", content = "(year)"),
        )
      ),
    )

    getEnumerations(varFields) shouldBe List("Vol. 1 (1908-1914)", "v.1:no.1 (1984) - v.35:no.2 (2018)")
  }

  def getEnumerations(varFields: List[VarField]): List[String] =
    SierraHoldingsEnumeration(createSierraHoldingsNumber, varFields)
}
