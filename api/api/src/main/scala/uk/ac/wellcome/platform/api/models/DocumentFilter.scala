package uk.ac.wellcome.platform.api.models

import uk.ac.wellcome.models.work.internal.{AccessStatus, WorkType}
import java.time.LocalDate

sealed trait WorkFilter

sealed trait ImageFilter

case class ItemLocationTypeIdFilter(locationTypeIds: Seq[String])
    extends WorkFilter

case class FormatFilter(formatIds: Seq[String]) extends WorkFilter

case class WorkTypeFilter(types: List[WorkType]) extends WorkFilter

case class DateRangeFilter(fromDate: Option[LocalDate],
                           toDate: Option[LocalDate])
    extends WorkFilter

case object VisibleWorkFilter extends WorkFilter

case class LanguagesFilter(languageIds: Seq[String]) extends WorkFilter

case class GenreFilter(genreQuery: Seq[String]) extends WorkFilter

case class SubjectFilter(subjectQuery: Seq[String]) extends WorkFilter

case class ContributorsFilter(contributorQueries: Seq[String])
    extends WorkFilter
    with ImageFilter

case class LicenseFilter(licenseIds: Seq[String])
    extends WorkFilter
    with ImageFilter

case class IdentifiersFilter(values: Seq[String]) extends WorkFilter

case class AccessStatusFilter(includes: List[AccessStatus],
                              excludes: List[AccessStatus])
    extends WorkFilter

case class PartOfFilter(id: String) extends WorkFilter

case class AvailabilitiesFilter(availabilityIds: Seq[String]) extends WorkFilter
