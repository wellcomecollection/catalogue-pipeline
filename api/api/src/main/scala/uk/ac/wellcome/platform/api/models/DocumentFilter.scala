package uk.ac.wellcome.platform.api.models

import java.time.LocalDate

sealed trait DocumentFilter

case class ItemLocationTypeFilter(locationTypeIds: Seq[String])
    extends DocumentFilter

case class WorkTypeFilter(workTypeIds: Seq[String]) extends DocumentFilter

case class DateRangeFilter(fromDate: Option[LocalDate],
                           toDate: Option[LocalDate])
    extends DocumentFilter

case object IdentifiedWorkFilter extends DocumentFilter

case class LanguageFilter(languageIds: Seq[String]) extends DocumentFilter

case class GenreFilter(genreQuery: String) extends DocumentFilter

case class SubjectFilter(subjectQuery: String) extends DocumentFilter

case class LicenseFilter(licenseIds: Seq[String]) extends DocumentFilter

case class CollectionPathFilter(path: String) extends DocumentFilter

case class CollectionDepthFilter(depth: Int) extends DocumentFilter
