package weco.catalogue.internal_model.work.generators

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{InstantRange, Period}

import java.time.{LocalDate, Month}

trait PeriodGenerators {
  def createPeriodForYear(year: String): Period[IdState.Minted] =
    Period(
      id = IdState.Unidentifiable,
      label = year,
      range = Some(
        InstantRange(
          from = LocalDate.of(year.toInt, Month.JANUARY, 1),
          to = LocalDate.of(year.toInt, Month.DECEMBER, 31),
          label = year
        )
      )
    )

  def createPeriodForYearRange(
    startYear: String,
    endYear: String
  ): Period[IdState.Minted] =
    Period(
      id = IdState.Unidentifiable,
      label = s"$startYear-$endYear",
      range = Some(
        InstantRange(
          from = LocalDate.of(startYear.toInt, Month.JANUARY, 1),
          to = LocalDate.of(endYear.toInt, Month.DECEMBER, 31),
          label = s"$startYear-$endYear"
        )
      )
    )
}
