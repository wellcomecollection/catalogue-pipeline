package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.languages.Language
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.data.SierraBibData
import weco.sierra.models.fields.SierraLanguage
import weco.sierra.models.marc.{Subfield, VarField}

class SierraLanguagesTest
    extends AnyFunSpec
    with Matchers
    with SierraDataGenerators {
  it("ignores records without any languages") {
    val bibData = createSierraBibDataWith(lang = None, varFields = List.empty)

    getLanguages(bibData) shouldBe empty
  }

  it("parses a single language from the 'lang' field") {
    // e.g. b11751198
    val bibData = createSierraBibDataWith(
      lang = Some(
        SierraLanguage(code = "fre", name = "French")
      ),
      varFields = List.empty
    )

    getLanguages(bibData) shouldBe List(Language(label = "French", id = "fre"))
  }

  it("combines the language from the 'lang' field and 041") {
    val bibData = createSierraBibDataWith(
      lang = Some(
        SierraLanguage(code = "fre", name = "French")
      ),
      varFields = List(
        VarField(
          marcTag = "041",
          subfields = List(
            Subfield(tag = "a", content = "ger"),
            Subfield(tag = "b", content = "dut"),
            Subfield(tag = "a", content = "eng")
          )
        )
      )
    )

    getLanguages(bibData) shouldBe List(
      Language(label = "French", id = "fre"),
      Language(label = "German", id = "ger"),
      Language(label = "English", id = "eng")
    )
  }

  it("gets languages from multiple instances of 041") {
    val bibData = createSierraBibDataWith(
      lang = Some(
        SierraLanguage(code = "fre", name = "French")
      ),
      varFields = List(
        VarField(
          marcTag = "041",
          subfields = List(Subfield(tag = "a", content = "ger"))
        ),
        VarField(
          marcTag = "041",
          subfields = List(Subfield(tag = "a", content = "eng"))
        )
      )
    )

    getLanguages(bibData) shouldBe List(
      Language(label = "French", id = "fre"),
      Language(label = "German", id = "ger"),
      Language(label = "English", id = "eng")
    )
  }

  it("ignores unrecognised language codes in 041") {
    val bibData = createSierraBibDataWith(
      lang = Some(
        SierraLanguage(code = "chi", name = "Chinese")
      ),
      varFields = List(
        VarField(
          marcTag = "041",
          subfields = List(
            Subfield(tag = "a", content = "???")
          )
        )
      )
    )

    getLanguages(bibData) shouldBe List(Language(label = "Chinese", id = "chi"))
  }

  it("deduplicates, putting whatever's in 'lang' first") {
    // e.g. b11953640
    val bibData = createSierraBibDataWith(
      lang = Some(
        SierraLanguage(code = "ger", name = "German")
      ),
      varFields = List(
        VarField(
          marcTag = "041",
          subfields = List(
            Subfield(tag = "a", content = "fre"),
            Subfield(tag = "a", content = "eng"),
            Subfield(tag = "a", content = "ger")
          )
        )
      )
    )

    getLanguages(bibData) shouldBe List(
      Language(label = "German", id = "ger"),
      Language(label = "French", id = "fre"),
      Language(label = "English", id = "eng")
    )
  }

  it("suppresses codes that don't correspond to languages") {
    val bibData = createSierraBibDataWith(
      lang = Some(
        SierraLanguage(code = "chi", name = "Chinese")
      ),
      varFields = List(
        VarField(
          marcTag = "041",
          subfields = List(
            Subfield(tag = "a", content = "mul"), // Multiple languages
            Subfield(tag = "a", content = "eng"),
            Subfield(tag = "a", content = "und"), // Undetermined
            Subfield(tag = "a", content = "fre"),
            Subfield(tag = "a", content = "zxx") // No linguistic content
          )
        )
      )
    )

    getLanguages(bibData) shouldBe List(
      Language(label = "Chinese", id = "chi"),
      Language(label = "English", id = "eng"),
      Language(label = "French", id = "fre")
    )
  }

  it("strips whitespace from values in the 041 field") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "041",
          subfields = List(
            Subfield(tag = "a", content = "eng "),
          )
        )
      )
    )

    getLanguages(bibData) shouldBe List(
      Language(label = "English", id = "eng"),
    )
  }

  it("lowercases values in the 041 field") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "041",
          subfields = List(
            Subfield(tag = "a", content = "ENG"),
            Subfield(tag = "a", content = "Lat"),
          )
        )
      )
    )

    getLanguages(bibData) shouldBe List(
      Language(label = "English", id = "eng"),
      Language(label = "Latin", id = "lat"),
    )
  }

  it("skips a language with no name and an unidentifiable code") {
    val bibData = createSierraBibDataWith(
      lang = Some(SierraLanguage(code = "idk", name = None))
    )

    getLanguages(bibData) shouldBe empty
  }

  it("ignores a language code which is only whitespace") {
    val bibData = createSierraBibDataWith(
      lang = Some(SierraLanguage(code = "   ", name = None))
    )

    getLanguages(bibData) shouldBe empty
  }

  private def getLanguages(bibData: SierraBibData): List[Language] =
    SierraLanguages(bibId = createSierraBibNumber, bibData = bibData)
}
