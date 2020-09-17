package uk.ac.wellcome.platform.api.models

import com.sksamuel.elastic4s.ElasticApi.{existsQuery, search}
import com.sksamuel.elastic4s.ElasticDsl.SearchHandler
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.{ElasticClient, Index}
import uk.ac.wellcome.models.work.internal.AugmentedImage
import uk.ac.wellcome.models.Implicits._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

case class QueryConfig(
  paletteBinSizes: Seq[Int]
)

object QueryConfig {
  def fetchFromIndex(elasticClient: ElasticClient, imagesIndex: Index)(
    implicit ec: ExecutionContext): QueryConfig =
    QueryConfig(
      paletteBinSizes = Try(
        Await.result(
          getPaletteBinSizesFromIndex(elasticClient, imagesIndex),
          5 seconds
        )
      ).getOrElse(defaultPaletteBinSizes)
    )

  val defaultPaletteBinSizes = Seq(4, 6, 8)

  private def getPaletteBinSizesFromIndex(
    elasticClient: ElasticClient,
    index: Index)(implicit ec: ExecutionContext): Future[Seq[Int]] =
    elasticClient
      .execute(
        search(index).query(
          existsQuery("inferredData.palette")
        )
      )
      .flatMap { result =>
        Future.fromTry {
          result.toEither
            .map { response =>
              response.hits.hits.headOption
                .flatMap(_.to[AugmentedImage].inferredData.map(_.palette))
                .map { palette =>
                  palette
                    .flatMap(_.split("/").lastOption)
                    .distinct
                    .map(_.toInt)
                }
            }
            .left
            .map(_.asException)
            .toTry
            .flatMap {
              case Some(bins) => Success(bins)
              case None =>
                Failure(
                  new RuntimeException(
                    "Could not extract palette parameters from data in index"
                  )
                )
            }
        }
      }
}
