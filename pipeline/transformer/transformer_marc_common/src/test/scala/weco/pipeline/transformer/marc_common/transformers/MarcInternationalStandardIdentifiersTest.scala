package weco.pipeline.transformer.marc_common.transformers

import org.scalatest.LoneElement
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{
  IdentifierType,
  SourceIdentifier
}
import weco.pipeline.transformer.marc_common.generators.MarcTestRecord
import weco.pipeline.transformer.marc_common.models.{MarcField, MarcSubfield}

class MarcInternationalStandardIdentifiersTest
    extends AnyFunSpec
    with Matchers
    with LoneElement {

  describe(
    "Extracting International Standard Numbers (ISBN & ISSN) from 020 and 022"
  ) {
    info("https://www.loc.gov/marc/bibliographic/bd020.html")
    info("https://www.loc.gov/marc/bibliographic/bd022.html")
    info("ISBNs and ISSNs represent Identifiers in the Internal Model")
    it("extracts ISBN from MARC 020") {
      val record = MarcTestRecord(fields =
        Seq(
          MarcField(
            "020",
            subfields =
              Seq(MarcSubfield(tag = "a", content = "978-1-890159-02-3"))
          )
        )
      )
      val sourceId: SourceIdentifier =
        MarcInternationalStandardIdentifiers(record).loneElement
      sourceId should have(
        'identifierType(IdentifierType.ISBN),
        'value("978-1-890159-02-3")
      )
    }
    it("extracts ISSN from MARC 022") {
      val record = MarcTestRecord(fields =
        Seq(
          MarcField(
            "022",
            subfields = Seq(MarcSubfield(tag = "a", content = "2153-3601"))
          )
        )
      )
      val sourceId: SourceIdentifier =
        MarcInternationalStandardIdentifiers(record).loneElement
      sourceId should have(
        'identifierType(IdentifierType.ISSN),
        'value("2153-3601")
      )
    }

    it("strips whitespace") {
      val record = MarcTestRecord(fields =
        Seq(
          MarcField(
            "020",
            subfields =
              Seq(MarcSubfield(tag = "a", content = "   978-1-890159-02-3   "))
          ),
          MarcField(
            "022",
            subfields = Seq(MarcSubfield(tag = "a", content = "  2153-3601  "))
          )
        )
      )
      MarcInternationalStandardIdentifiers(record).map(
        _.value
      ) should contain theSameElementsAs Seq("978-1-890159-02-3", "2153-3601")

    }

    it("can extract multiple identifiers") {
      info("both 020 and 022 are repeatable")
      val record = MarcTestRecord(fields =
        Seq(
          MarcField(
            "020",
            subfields =
              Seq(MarcSubfield(tag = "a", content = "   0870334336   "))
          ),
          MarcField(
            "022",
            subfields = Seq(MarcSubfield(tag = "a", content = "   0026-9662"))
          ),
          MarcField(
            "020",
            subfields =
              Seq(MarcSubfield(tag = "a", content = "   978-1-890159-02-3   "))
          ),
          MarcField(
            "022",
            subfields = Seq(MarcSubfield(tag = "a", content = "2153-3601"))
          )
        )
      )
      MarcInternationalStandardIdentifiers(record).map(
        _.value
      ) should contain theSameElementsAs Seq(
        "0870334336",
        "0026-9662",
        "978-1-890159-02-3",
        "2153-3601"
      )
    }
    it("deduplicates identifiers") {
      info("both 020 and 022 are repeatable,")
      info("but we don't want to extract the same ID multiple times")
      val record = MarcTestRecord(fields =
        Seq(
          MarcField(
            "020",
            subfields =
              Seq(MarcSubfield(tag = "a", content = "   9781960227966   "))
          ),
          MarcField(
            "022",
            subfields = Seq(MarcSubfield(tag = "a", content = "   0026-9662"))
          ),
          MarcField(
            "020",
            subfields = Seq(MarcSubfield(tag = "a", content = "9781960227966 "))
          ),
          MarcField(
            "022",
            subfields = Seq(MarcSubfield(tag = "a", content = "0026-9662"))
          )
        )
      )
      MarcInternationalStandardIdentifiers(record).map(
        _.value
      ) should contain theSameElementsAs Seq(
        "9781960227966",
        "0026-9662"
      )
    }

    it("ignores cancelled identifiers in $z") {
      val record = MarcTestRecord(fields =
        Seq(
          MarcField(
            "020",
            subfields = Seq(MarcSubfield(tag = "z", content = "No!"))
          ),
          MarcField(
            "022",
            subfields = Seq(MarcSubfield(tag = "a", content = "   0026-9662"))
          ),
          MarcField(
            "020",
            subfields = Seq(MarcSubfield(tag = "a", content = "9781960227966 "))
          ),
          MarcField(
            "022",
            subfields = Seq(
              MarcSubfield(
                tag = "z",
                content = "For more information, please re-read"
              )
            )
          )
        )
      )
      MarcInternationalStandardIdentifiers(record).map(
        _.value
      ) should contain theSameElementsAs Seq(
        "9781960227966",
        "0026-9662"
      )

    }
  }

}
