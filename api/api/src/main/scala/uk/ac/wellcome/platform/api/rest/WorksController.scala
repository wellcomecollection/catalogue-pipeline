package uk.ac.wellcome.platform.api.rest

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.model.StatusCodes.Found
import akka.http.scaladsl.server.Route
import com.sksamuel.elastic4s.Index
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import uk.ac.wellcome.display.models._
import uk.ac.wellcome.display.models.Implicits._
import uk.ac.wellcome.platform.api.models.ApiConfig
import uk.ac.wellcome.platform.api.services.{ElasticsearchService, WorksService}
import uk.ac.wellcome.platform.api.Tracing
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Indexed

class WorksController(elasticsearchService: ElasticsearchService,
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
                    includes = params.include.getOrElse(WorksIncludes.none),
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
        val includes = params.include.getOrElse(WorksIncludes.none)
        worksService
          .findWorkById(id)(index)
          .map {
            case Right(Some(work: Work.Visible[Indexed])) =>
              workFound(work, includes)
            case Right(Some(work: Work.Redirected[Indexed])) =>
              workRedirect(work)
            case Right(Some(_: Work.Invisible[Indexed])) =>
              gone("This work has been deleted")
            case Right(Some(_: Work.Deleted[Indexed])) =>
              gone("This work has been deleted")
            case Right(None) =>
              notFound(s"Work not found for identifier $id")
            case Left(err) => elasticError(err)
          }
      }
    }

  def workRedirect(work: Work.Redirected[Indexed]): Route =
    extractUri { uri =>
      val newPath =
        (work.redirectTarget.canonicalId :: uri.path.reverse.tail).reverse

      // We use a relative URL here so that redirects keep you on the same host.
      //
      // e.g. on https://api-stage.wc.org, we tell the API that it's running at
      // https://api.wc.org so URLs in responses look the same, but we don't want
      // the stage API to send redirects to the prod API.
      //
      // Relative URLs are explicitly supported in HTTP 302 Redirects, see
      // https://greenbytes.de/tech/webdav/draft-ietf-httpbis-p2-semantics-17.html#rfc.section.9.5
      // https://stackoverflow.com/q/8250259/1558022
      redirect(uri.withPath(newPath).toRelative, Found)
    }

  def workFound(work: Work.Visible[Indexed], includes: WorksIncludes): Route =
    complete(
      ResultResponse(
        context = contextUri,
        result = DisplayWork(work, includes)
      )
    )

  private lazy val worksService = new WorksService(elasticsearchService)
}
