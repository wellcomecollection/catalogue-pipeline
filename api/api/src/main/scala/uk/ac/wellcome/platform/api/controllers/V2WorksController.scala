package uk.ac.wellcome.platform.api.controllers

import com.google.inject.{Inject, Singleton}
import io.swagger.models.{Operation, Swagger}
import uk.ac.wellcome.display.models.{ApiVersions, DisplayWork, V2WorksIncludes}
import uk.ac.wellcome.display.models.v2.DisplayWorkV2
import uk.ac.wellcome.elasticsearch.DisplayElasticConfig
import uk.ac.wellcome.platform.api.models._
import uk.ac.wellcome.platform.api.requests.{
  V2MultipleResultsRequest,
  V2SingleWorkRequest
}
import uk.ac.wellcome.platform.api.services.WorksService

import scala.concurrent.ExecutionContext
import scala.reflect.runtime.universe.TypeTag

@Singleton
class V2WorksController @Inject()(
  apiConfig: ApiConfig,
  elasticConfig: DisplayElasticConfig,
  worksService: WorksService)(implicit ec: ExecutionContext)
    extends WorksController[
      V2MultipleResultsRequest,
      V2SingleWorkRequest,
      V2WorksIncludes](
      apiConfig = apiConfig,
      defaultIndex = elasticConfig.indexV2,
      worksService = worksService
    ) {
  implicit protected val swagger = ApiV2Swagger

  override def emptyWorksIncludes: V2WorksIncludes = V2WorksIncludes.apply()
  override def recognisedIncludes: List[String] =
    V2WorksIncludes.recognisedIncludes
  lazy override protected val includeParameterName: String = "include"

  prefix(s"${apiConfig.pathPrefix}/${ApiVersions.v2.toString}") {
    setupResultListEndpoint(ApiVersions.v2, "/works", DisplayWorkV2.apply)
    setupSingleWorkEndpoint(ApiVersions.v2, "/works/:id", DisplayWorkV2.apply)
  }

  override def buildFilters(
    request: V2MultipleResultsRequest): List[WorkFilter] = {

    val itemLocationTypeFilter =
      request.itemLocationType.map(ItemLocationTypeFilter(_))

    val workTypeFilter =
      request.itemLocationType.map(WorkTypeFilter(_))

    val dateRangeFilter =
      (request.productionDateFrom, request.productionDateTo) match {
        case (None, None)       => None
        case (dateFrom, dateTo) => Some(DateRangeFilter(dateFrom, dateTo))
      }

    val languageFilter =
      request.language.map(LanguageFilter(_))

    val genreFilter =
      request.genre.map(GenreFilter(_))

    val subjectFilter =
      request.subject.map(SubjectFilter(_))

    List(
      itemLocationTypeFilter,
      workTypeFilter,
      dateRangeFilter,
      languageFilter,
      genreFilter,
      subjectFilter
    ).flatten
  }

  override def setupResultListSwaggerDocs[T <: DisplayWork](
    endpointSuffix: String,
    swagger: Swagger,
    doc: Operation)(
    implicit evidence: TypeTag[DisplayResultList[T]]): Operation = {
    implicit val finatraSwagger = swagger

    super
      .setupResultListSwaggerDocs(endpointSuffix, swagger, doc)(evidence)
      .queryParam[String](
        "items.locations.locationType",
        "Filter by the LocationType of items on the retrieved works",
        required = false
      )
      .queryParam[String](
        "workType",
        "Filter by the workType of the searched works",
        required = false
      )
  }
}
