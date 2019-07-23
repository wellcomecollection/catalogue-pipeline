package uk.ac.wellcome.platform.api.models

import java.time.LocalDate

import org.scalatest.FunSpec

class CenturyAggregationTest extends FunSpec {
  it("maps across centuries") {
    CenturyAggregation(
      LocalDate.of(1600, 1, 1),
      LocalDate.of(2000, 1, 1)
    ) map { century =>
      }
  }
}
