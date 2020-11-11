package uk.ac.wellcome.platform.api.rest

import scala.concurrent.{ExecutionContext, Future}

import akka.http.scaladsl.model.StatusCodes.Found
import akka.http.scaladsl.server.Route
import com.sksamuel.elastic4s.Index
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import grizzled.slf4j.Logger
import uk.ac.wellcome.display.models._
import uk.ac.wellcome.display.models.Implicits._
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.api.models.ApiConfig
import uk.ac.wellcome.platform.api.services.{
  ElasticsearchService,
  RelatedWorkService,
  WorksService
}
import uk.ac.wellcome.platform.api.Tracing
import WorkState.Identified

class WorksController(
  elasticsearchService: ElasticsearchService,
  implicit val apiConfig: ApiConfig,
  worksIndex: Index)(implicit ec: ExecutionContext)
    extends Tracing
    with CustomDirectives
    with FailFastCirceSupport {
  import DisplayResultList.encoder
  import ResultResponse.encoder

  def multipleWorks(params: MultipleWorksParams): Route =
    getWithFuture {
      transactFuture("GET /works") {
        val searchOptions = params.searchOptions(apiConfig)
        val index =
          params._index.map(Index(_)).getOrElse(worksIndex)
        worksService
          .listOrSearchWorks(index, searchOptions)
          .map {
            case Left(err) => elasticError(err)
            case Right(resultList) =>
              extractPublicUri { requestUri =>
                complete(
                  DisplayResultList(
                    resultList = resultList,
                    searchOptions = searchOptions,
                    includes = params.include.getOrElse(WorksIncludes()),
                    requestUri = requestUri,
                    contextUri = contextUri
                  )
                )
              }
          }
      }
    }

  def singleWork(id: String, params: SingleWorkParams): Route =
    getWithFuture {
      transactFuture("GET /works/{workId}") {
        val index =
          params._index.map(Index(_)).getOrElse(worksIndex)
        val includes = params.include.getOrElse(WorksIncludes())
        worksService
          .findWorkById(id)(index)
          .flatMap {
            case Right(Some(work: Work.Visible[Identified])) =>
              if (includes.anyRelation) {
                retrieveRelatedWorks(index, work).map { relatedWorks =>
                  workFound(work, relatedWorks, includes)
                }
              } else
                Future.successful(workFound(work, None, includes))
            case Right(Some(work: Work.Redirected[Identified])) =>
              Future.successful(workRedirect(work))
            case Right(Some(work: Work.Invisible[Identified])) =>
              Future.successful(gone("This work has been deleted"))
            case Right(None) =>
              Future.successful(notFound(s"Work not found for identifier $id"))
            case Left(err) => Future.successful(elasticError(err))
          }
      }
    }

  private def retrieveRelatedWorks(
    index: Index,
    work: Work.Visible[Identified]): Future[Option[RelatedWorks]] =
    relatedWorkService
      .retrieveRelatedWorks(index, work)
      .map {
        case Left(err) =>
          // We just log this here rather than raising so as not to bring down
          // the work API when related work retrieval fails
          logger.error("Error retrieving related works", err)
          None
        case Right(relatedWorks) => Some(relatedWorks)
      }

  def workRedirect(work: Work.Redirected[Identified]): Route =
    extractPublicUri { uri =>
      val newPath = (work.redirect.canonicalId :: uri.path.reverse.tail).reverse
      redirect(uri.withPath(newPath), Found)
    }

  def workFound(work: Work.Visible[Identified],
                relatedWorks: Option[RelatedWorks],
                includes: WorksIncludes): Route =
    complete(
      ResultResponse(
        context = contextUri,
        result = relatedWorks
          .map(DisplayWork(work, includes, _))
          .getOrElse(DisplayWork(work, includes))
      )
    )

  private lazy val relatedWorkService =
    new RelatedWorkService(elasticsearchService)

  private lazy val worksService = new WorksService(elasticsearchService)

  private lazy val logger = Logger(this.getClass.getName)
}
