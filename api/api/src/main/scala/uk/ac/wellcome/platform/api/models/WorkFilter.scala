package uk.ac.wellcome.platform.api.models

import java.time.LocalDate

sealed trait WorkFilter

case class ItemLocationTypeFilter(locationTypeIds: Seq[String])
    extends WorkFilter

case class WorkTypeFilter(workTypeIds: Seq[String]) extends WorkFilter

case class DateRangeFilter(fromDate: Option[LocalDate],
                           toDate: Option[LocalDate])
    extends WorkFilter

case object IdentifiedWorkFilter extends WorkFilter

case class LanguageFilter(languageIds: Seq[String]) extends WorkFilter

case class GenreFilter(genreQuery: String) extends WorkFilter

case class SubjectFilter(subjectQuery: String) extends WorkFilter

case class LicenseFilter(licenseIds: Seq[String]) extends WorkFilter

case class CollectionPathFilter(path: String) extends WorkFilter

case class CollectionDepthFilter(depth: Int) extends WorkFilter
