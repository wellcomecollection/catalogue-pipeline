package weco.pipeline.transformer.calm

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class NormaliseTextTest extends AnyFunSpec with Matchers {

  it("preserves basic text tags") {
    val str = "Some text with some <em>basic text tags</em> inside."
    NormaliseText(str) shouldBe str
  }

  it("strips non-basic text tags") {
    val str = "<div>Tags should be <in>removed</in></div>"
    NormaliseText(str) shouldBe "Tags should be removed"
  }

  it("removes script tags and inner content") {
    val str =
      """Nothing here...<script>window.alert("NASTY");</script> Nothing."""
    NormaliseText(str) shouldBe "Nothing here... Nothing."
  }

  it("does not error when missing closing tags") {
    val str = "Someone forgot <em>to close something.."
    NormaliseText(str) shouldBe "Someone forgot <em>to close something..</em>"
  }

  it("preserves newlines and left space in text") {
    val str = """Text
      |  * that spans
      |  * multiple lines
      |(and includes some <b>left whitespace</b>)""".stripMargin
    NormaliseText(str) shouldBe str
  }

  it("retains links, stripping all non href attributes") {
    val str = """A <a href="http://example.location" target="_blank">link</a>"""
    NormaliseText(
      str
    ) shouldBe """A <a href="http://example.location" rel="nofollow">link</a>"""
  }

  it("does not escape symbols") {
    val str = "Ampersand & some other symbols like >."
    NormaliseText(str) shouldBe str
  }

  it("strips whole html document, removing recurring empty lines") {
    val str =
      """
      <!doctype html>
      <html lang="en">
      <head>
        <meta charset="utf-8">
      </head>
      <div>
        <h1>The document:</h1>
        <div>
          <p>
            This contains text with <em>a few</em> <b>different</b> tags, some of
            of which are <span>preserved</span>, others <ins>not</ins>.
          </p>
        </div>
      </div>
    """
    NormaliseText(str) shouldBe """The document:

          <p>
            This contains text with <em>a few</em> <b>different</b> tags, some of
            of which are <span>preserved</span>, others not.
          </p>"""
  }
}
