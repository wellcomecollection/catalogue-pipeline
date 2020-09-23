package uk.ac.wellcome.platform.api.models

import uk.ac.wellcome.models.work.internal.{AccessStatus, WorkType}
import java.time.LocalDate

import uk.ac.wellcome.display.models.LocationTypeQuery

sealed trait DocumentFilter
sealed trait WorkFilter extends DocumentFilter
sealed trait ImageFilter extends DocumentFilter

case class ItemLocationTypeFilter(locationTypes: Seq[LocationTypeQuery])
    extends WorkFilter

case class ItemLocationTypeIdFilter(locationTypeIds: Seq[String])
    extends WorkFilter

case class FormatFilter(formatIds: Seq[String]) extends WorkFilter

case class WorkTypeFilter(types: List[WorkType]) extends WorkFilter

case class DateRangeFilter(fromDate: Option[LocalDate],
                           toDate: Option[LocalDate])
    extends WorkFilter

case object VisibleWorkFilter extends WorkFilter

case class LanguageFilter(languageIds: Seq[String]) extends WorkFilter

case class GenreFilter(genreQuery: String) extends WorkFilter

case class SubjectFilter(subjectQuery: String) extends WorkFilter

case class LicenseFilter(licenseIds: Seq[String])
    extends WorkFilter
    with ImageFilter

case class IdentifiersFilter(values: Seq[String]) extends WorkFilter

case class CollectionPathFilter(path: String) extends WorkFilter

case class CollectionDepthFilter(depth: Int) extends WorkFilter

case class AccessStatusFilter(includes: List[AccessStatus],
                              excludes: List[AccessStatus])
    extends WorkFilter

case class ColorFilter(hexColors: Seq[String]) extends ImageFilter
