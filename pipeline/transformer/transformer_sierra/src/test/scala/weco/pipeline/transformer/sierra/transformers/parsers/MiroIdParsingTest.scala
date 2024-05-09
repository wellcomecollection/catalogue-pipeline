package weco.pipeline.transformer.sierra.transformers.parsers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.matchers.should.Matchers

class MiroIdParsingTest extends AnyFunSpec with Matchers {
  import MiroIdParsing._

  it("parses miroIds") {
    forAll(
      Table(
        ("miroId", "parsedId"),
        ("V1234567", "V1234567"),
        ("V 1234567", "V1234567"),
        ("V 1", "V0000001"),
        ("V 12345", "V0012345"),
        ("L 12345", "L0012345"),
        ("V 12345 ER", "V0012345ER")
      )
    ) {
      (miroId, expectedParsedId) =>
        maybeFromString(miroId) shouldBe Some(expectedParsedId)
    }
  }

  it("parses IDs from URLs") {
    forAll(
      Table(
        ("URL", "Parsed ID"),
        ("http://wellcomeimages.org/indexplus/image/A0123456.html", "A0123456"),
        (
          "http://wellcomeimages.org/ixbin/hixclient?MIROPAC=V1234567",
          "V1234567"
        ),
        (
          "http://wellcomeimages.org/ixbinixclient.exe?MIROPAC=V0010851.html.html",
          "V0010851"
        ),
        (
          "http://wellcomeimages.org/ixbinixclient.exe?image=M0009946.html",
          "M0009946"
        )
      )
    ) {
      (url, expected) =>
        maybeFromURL(url) shouldBe Some(expected)
    }
  }

  it("strips suffixes from IDs if present") {
    val suffixed = "V0036036EL"
    val nonSuffixed = "V0036036"

    stripSuffix(suffixed) shouldBe nonSuffixed
    stripSuffix(nonSuffixed) shouldBe nonSuffixed
  }

  it("does not parse invalid miroIds") {
    forAll(
      Table(
        "miroId",
        "",
        "11",
        "VV"
      )
    ) {
      invalidMiroId =>
        maybeFromString(invalidMiroId) shouldBe None
    }
  }

  it("does not parse IDs from non-Wellcome-Images URLs") {
    val url =
      "http://film.wellcome.ac.uk:15151/mediaplayer.html?fug_7340-1&pw=524ph=600.html"
    maybeFromURL(url) shouldBe None
  }
}
