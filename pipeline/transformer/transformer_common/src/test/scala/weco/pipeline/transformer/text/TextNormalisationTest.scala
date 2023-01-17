package weco.pipeline.transformer.text

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.matchers.should.Matchers
import TextNormalisation._

class TextNormalisationTest extends AnyFunSpec with Matchers {
  describe("trimTrailing") {
    it("removes trailing character") {
      val examples = Table(
        ("-in-", "-out-"),
        ("text", "text"),
        ("text.", "text"),
        (" text. ", " text"),
        ("text. ", "text"),
        ("text.  ", "text"),
        ("text . ", "text"),
        ("text.\t", "text"),
        ("text.\n", "text"),
        ("text.\r", "text"),
        ("text.\f", "text"),
        ("text .", "text"),
        (" text", " text"),
        (" \ttext.", " \ttext"),
        ("te xt.", "te xt"),
        ("text . ", "text"),
        ("text ,. ", "text ,"),
        ("text.  ", "text"),
        ("text..  ", "text."),
        (".text", ".text"),
        (".text.", ".text"),
        (".text..", ".text."),
        (".text. . ", ".text."),
        (".", ""),
        ("..", "."),
        ("a title ... with ... . ", "a title ... with ..."),
        ("a title ... with .... ", "a title ... with ...")
      )
      forAll(examples) { (i: String, o: String) =>
        i.trimTrailing('.') shouldBe o
      }
    }

    it("removes trailing literal regexp character") {
      val examples = Table(
        ("-in-", "-char-"),
        ("text\\", '\\'),
        ("text^", '^')
      )
      forAll(examples) { (i: String, c: Char) =>
        i.trimTrailing(c) shouldBe "text"
      }
    }
  }

  describe("trimTrailingPeriod") {
    it("removes a single trailing period") {
      val examples = Table(
        ("text", "text"),
        ("text.", "text"),
        (" text. ", " text"),
        ("text. ", "text"),
        ("text . ", "text"),
      )
      forAll(examples) { (i: String, o: String) =>
        i.trimTrailingPeriod shouldBe o
      }
    }

    it("doesn't remove an ellipsis") {
      val examples = Table(
        ("text...", "text..."),
        ("text... ", "text...")
      )
      forAll(examples) { (i: String, o: String) =>
        i.trimTrailingPeriod shouldBe o
      }
    }
  }

  describe("sentenceCase") {
    it("converts to sentence case") {
      val examples = Table(
        ("-in-", "-out-"),
        ("text", "Text"),
        ("TEXT", "TEXT"),
        ("teXT", "TeXT"),
        ("text text", "Text text"),
        ("Text Text", "Text Text"),
        ("Text teXT", "Text teXT")
      )
      forAll(examples) { (i: String, o: String) =>
        i.sentenceCase shouldBe o
      }
    }
  }
}
