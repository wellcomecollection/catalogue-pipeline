package uk.ac.wellcome.platform.api.rest

import akka.http.scaladsl.model.StatusCodes.Found
import akka.http.scaladsl.server.Route
import com.sksamuel.elastic4s.Index
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import grizzled.slf4j.Logger
import uk.ac.wellcome.display.models._
import uk.ac.wellcome.display.models.Implicits._
import uk.ac.wellcome.elasticsearch.ElasticConfig
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.api.models.ApiConfig
import uk.ac.wellcome.platform.api.services.{
  CollectionService,
  ElasticsearchService,
  WorksService,
  RelatedWorkService,
  RelatedWorks
}
import uk.ac.wellcome.platform.api.Tracing

import scala.concurrent.{ExecutionContext, Future}

class WorksController(
  elasticsearchService: ElasticsearchService,
  implicit val apiConfig: ApiConfig,
  elasticConfig: ElasticConfig)(implicit ec: ExecutionContext)
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
          params._index.map(Index(_)).getOrElse(elasticConfig.worksIndex)
        worksService
          .listOrSearchWorks(index, searchOptions)
          .map {
            case Left(err) => elasticError(err)
            case Right(resultList) =>
              extractPublicUri { uri =>
                complete(
                  DisplayResultList(
                    resultList,
                    searchOptions,
                    params.include.getOrElse(WorksIncludes()),
                    uri,
                    contextUri
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
          params._index.map(Index(_)).getOrElse(elasticConfig.worksIndex)
        val includes = params.include.getOrElse(WorksIncludes())
        worksService
          .findWorkById(id)(index)
          .flatMap {
            case Right(Some(work: IdentifiedWork)) =>
              if (includes.anyRelation) {
                retrieveRelatedWorks(index, work).map { relatedWorks =>
                  workFound(work, relatedWorks, None, includes)
                }
              }
              else if (includes.collection) {
                val expandedPaths = params._expandPaths.getOrElse(Nil)
                retrieveTree(index, work, expandedPaths).map { tree =>
                  workFound(work, None, tree, includes)
                }
              } else
                Future.successful(workFound(work, None, None, includes))
            case Right(Some(work: IdentifiedRedirectedWork)) =>
              Future.successful(workRedirect(work))
            case Right(Some(_)) =>
              Future.successful(gone("This work has been deleted"))
            case Right(None) =>
              Future.successful(
                notFound(s"Work not found for identifier ${id}"))
            case Left(err) => Future.successful(elasticError(err))
          }
      }
    }

  private def retrieveTree(
    index: Index,
    work: IdentifiedWork,
    expandedPaths: List[String]): Future[Option[(Collection, List[String])]] =
    work.data.collectionPath
      .map {
        case CollectionPath(path, _, _) =>
          val allPaths = path :: expandedPaths
          collectionService.retrieveTree(index, allPaths).map {
            case Left(err) =>
              // We just log this here rather than raising so as not to bring down
              // the work API when tree retrieval fails
              logger.error("Error retrieving collection tree", err)
              None
            case Right(tree) => Some((tree, allPaths))
          }
      }
      .getOrElse(Future.successful(None))

  private def retrieveRelatedWorks(
    index: Index,
    work: IdentifiedWork): Future[Option[RelatedWorks]] =
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

  def workRedirect(work: IdentifiedRedirectedWork): Route =
    extractPublicUri { uri =>
      val newPath = (work.redirect.canonicalId :: uri.path.reverse.tail).reverse
      redirect(uri.withPath(newPath), Found)
    }

  def workFound(work: IdentifiedWork,
                relatedWorks: Option[RelatedWorks],
                tree: Option[(Collection, List[String])],
                includes: WorksIncludes): Route =
    complete(
      ResultResponse(
        context = contextUri,
        result = DisplayWork(work, includes).copy(
          collection = tree.map {
            case (tree, expandedPaths) =>
              DisplayCollection(tree, expandedPaths)
          },
          parts = relatedWorks.map { relatedWorks =>
            relatedWorks.parts.map(DisplayWork(_))
          },
          partOf = relatedWorks.map { relatedWorks =>
            relatedWorks.partOf.map(DisplayWork(_))
          },
          preceededBy = relatedWorks.map { relatedWorks =>
            relatedWorks.preceededBy.map(DisplayWork(_))
          },
          suceededBy = relatedWorks.map { relatedWorks =>
            relatedWorks.suceededBy.map(DisplayWork(_))
          },
        )
      )
    )

  private lazy val collectionService =
    new CollectionService(elasticsearchService)

  private lazy val relatedWorkService =
    new RelatedWorkService(elasticsearchService)

  private lazy val worksService = new WorksService(elasticsearchService)

  private lazy val logger = Logger(this.getClass.getName)
}
