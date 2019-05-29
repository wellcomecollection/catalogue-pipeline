package uk.ac.wellcome.models.work.internal

import java.time.{LocalDateTime, ZoneOffset}

import org.scalatest.{FunSpec, Matchers}

class DateRangeTest extends FunSpec with Matchers {

  describe("parse") {
    it(
      "parses a year like 1909 into a range of the beginning and end of that year") {
      val label = "1909"
      val expected = DateRange(
        label = "1909",
        from = LocalDateTime.parse("1909-01-01T00:00").toInstant(ZoneOffset.UTC),
        to = LocalDateTime
          .parse("1909-12-31T23:59:59.999999999")
          .toInstant(ZoneOffset.UTC),
        inferred = false
      )

      DateRange.parse(label) shouldBe Some(expected)

    }

    it(
      "parses a year like [1918] into a range of the beginning and end of that year and marks it as inferred") {
      val label = "[1918]"
      val expected = DateRange(
        label = "[1918]",
        from = LocalDateTime.parse("1918-01-01T00:00").toInstant(ZoneOffset.UTC),
        to = LocalDateTime
          .parse("1918-12-31T23:59:59.999999999")
          .toInstant(ZoneOffset.UTC),
        inferred = true
      )

      DateRange.parse(label) shouldBe Some(expected)

    }
  }
}
