package uk.ac.wellcome.platform.api.models

import uk.ac.wellcome.models.work.internal.AccessStatus

import java.time.LocalDate

sealed trait DocumentFilter
sealed trait WorkFilter extends DocumentFilter
sealed trait ImageFilter extends DocumentFilter

trait IncludeExcludeFilter[T] {
  val include: List[T]
  val exclude: List[T]

  import com.sksamuel.elastic4s.ElasticDsl._

  def toElasticString(obj: T): String

  def toElasticQuery(field: String) =
    (include.map(toElasticString(_)), exclude.map(toElasticString(_))) match {
      case (include, Nil) =>
        termsQuery(field = field, values = include)
      case (Nil, exclude) =>
        not(termsQuery(field = field, values = exclude))
      case (include, exclude) =>
        must(
          termsQuery(field = field, values = include),
          not(termsQuery(field = field, values = exclude)))
    }
}

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

case class LicenseFilter(licenseIds: Seq[String])
    extends WorkFilter
    with ImageFilter

case class IdentifiersFilter(values: List[String]) extends WorkFilter

case class CollectionPathFilter(path: String) extends WorkFilter

case class CollectionDepthFilter(depth: Int) extends WorkFilter

case class AccessStatusFilter(val include: List[AccessStatus],
                              val exclude: List[AccessStatus])
    extends WorkFilter
    with IncludeExcludeFilter[AccessStatus] {
  def toElasticString(status: AccessStatus) =
    status.getClass.getSimpleName.stripSuffix("$")
}
