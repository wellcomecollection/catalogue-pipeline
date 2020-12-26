package uk.ac.wellcome.models.parse

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.parse.parsers.DateParser

import java.io._
import java.util.zip._

class DateParserCoverageTest extends AnyFunSpec with Matchers {

  // See https://stackoverflow.com/q/17436549/1558022
  class BufferedReaderIterator(reader: BufferedReader) extends Iterator[String] {
    override def hasNext: Boolean = reader.ready
    override def next: String = reader.readLine()
  }

  /** This test runs against every Period label from the public catalogue snapshot,
    * and checks it can be parsed.
    *
    * The input for this test was created by running the bash script
    *
    *     curl 'https://data.wellcomecollection.org/catalogue/v2/works.json.gz' \
    *       | gunzip \
    *       | /usr/bin/jq -r '.production[].dates[].label' \
    *       | sort \
    *       | gzip > period_labels.txt.gz
    *
    * It isn't run by default because it's not very useful in checking the
    * correctness of the code, but it gives us an idea of how many labels we're
    * able to parse.  Use this if you're trying to measure the scope of the parser.
    *
    */
  it("parses all the dates in the public catalogue") {
    val lines: Iterator[String] =
      new BufferedReaderIterator(
        new BufferedReader(
          new InputStreamReader(
            new GZIPInputStream(
              getClass.getResourceAsStream("/period_labels.txt.gz")
            )
          )
        )
      )

    var parsed = 0
    var notParsed = 0

    lines
      .filter { _.nonEmpty }
      .foreach { label =>
        DateParser(label) match {
          case Some(_) => parsed += 1
          case None    => notParsed += 1
        }
      }

    println(s"""
      |DateParser coverage:
      |Labels parsed     = $parsed
      |Labels not parsed = $notParsed
      |Total             = ${parsed + notParsed}""".stripMargin)
  }
}
