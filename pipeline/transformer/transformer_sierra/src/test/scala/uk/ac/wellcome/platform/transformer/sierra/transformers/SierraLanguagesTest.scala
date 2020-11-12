package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.internal.Language
import uk.ac.wellcome.platform.transformer.sierra.generators.{MarcGenerators, SierraDataGenerators}
import uk.ac.wellcome.platform.transformer.sierra.source.MarcSubfield
import uk.ac.wellcome.platform.transformer.sierra.source.sierra.SierraSourceLanguage

class SierraLanguagesTest extends AnyFunSpec with Matchers with MarcGenerators with SierraDataGenerators {
  it("ignores records without any languages") {
    val bibData = createSierraBibDataWith(lang = None, varFields = List.empty)

    SierraLanguages(bibData) shouldBe empty
  }

  it("parses a single language from the 'lang' field") {
    // e.g. b11751198
    val bibData = createSierraBibDataWith(
      lang = Some(
        SierraSourceLanguage(code = "fre", name = "French")
      ),
      varFields = List.empty
    )

    SierraLanguages(bibData) shouldBe List(Language(label = "French", id = "fre"))
  }

  it("combines the language from the 'lang' field and 041") {
    val bibData = createSierraBibDataWith(
      lang = Some(
        SierraSourceLanguage(code = "fre", name = "French")
      ),
      varFields = List(
        createVarFieldWith(
          marcTag = "041",
          subfields = List(
            MarcSubfield(tag = "a", content = "ger"),
            MarcSubfield(tag = "b", content = "dut"),
            MarcSubfield(tag = "a", content = "eng")
          )
        )
      )
    )

    SierraLanguages(bibData) shouldBe List(
      Language(label = "French", id = "fre"),
      Language(label = "German", id = "ger"),
      Language(label = "English", id = "eng")
    )
  }

  it("ignores unrecognised language codes in 041") {
    val bibData = createSierraBibDataWith(
      lang = Some(
        SierraSourceLanguage(code = "chi", name = "Chinese")
      ),
      varFields = List(
        createVarFieldWith(
          marcTag = "041",
          subfields = List(
            MarcSubfield(tag = "a", content = "???")
          )
        )
      )
    )

    SierraLanguages(bibData) shouldBe List(Language(label = "Chinese", id = "chi"))
  }

  it("deduplicates, putting whatever's in 'lang' first") {
    // e.g. b11953640
    val bibData = createSierraBibDataWith(
      lang = Some(
        SierraSourceLanguage(code = "ger", name = "German")
      ),
      varFields = List(
        createVarFieldWith(
          marcTag = "041",
          subfields = List(
            MarcSubfield(tag = "a", content = "fre"),
            MarcSubfield(tag = "a", content = "eng"),
            MarcSubfield(tag = "a", content = "ger")
          )
        )
      )
    )

    SierraLanguages(bibData) shouldBe List(
      Language(label = "German", id = "ger"),
      Language(label = "French", id = "fre"),
      Language(label = "English", id = "eng")
    )
  }
}
