package uk.ac.wellcome.platform.api.models

import com.sksamuel.elastic4s.ElasticApi.{existsQuery, search}
import com.sksamuel.elastic4s.ElasticDsl.SearchHandler
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.{ElasticClient, Index}
import uk.ac.wellcome.models.Implicits._
import weco.catalogue.internal_model.image.{Image, ImageState, InferredData}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

case class QueryConfig(
  paletteBinSizes: Seq[Seq[Int]],
  paletteBinMinima: Seq[Float]
)

object QueryConfig {
  def fetchFromIndex(elasticClient: ElasticClient, imagesIndex: Index)(
    implicit ec: ExecutionContext): QueryConfig = {
    val (binSizes, binMinima) = Try(
      Await.result(
        getPaletteParamsFromIndex(elasticClient, imagesIndex),
        5 seconds
      )
    ).getOrElse((defaultPaletteBinSizes, defaultPaletteBinMinima))
    QueryConfig(
      paletteBinSizes = binSizes,
      paletteBinMinima = binMinima
    )
  }

  val defaultPaletteBinSizes = Seq(Seq(4, 6, 9), Seq(2, 4, 6), Seq(1, 3, 5))
  val defaultPaletteBinMinima = Seq(0f, 10f / 256, 10f / 256)

  private def getPaletteParamsFromIndex(elasticClient: ElasticClient,
                                        index: Index)(
    implicit ec: ExecutionContext): Future[(Seq[Seq[Int]], Seq[Float])] =
    elasticClient
      .execute(
        search(index).query(
          existsQuery("state.inferredData.palette")
        )
      )
      .flatMap { result =>
        Future.fromTry {
          result.toEither
            .map { response =>
              response.hits.hits.headOption
                .flatMap {
                  _.to[Image[ImageState.Indexed]].state.inferredData.flatMap {
                    case InferredData(_, _, _, _, binSizes, binMinima)
                        if binSizes.size == 3 &&
                          binSizes.forall(_.size == 3) &&
                          binMinima.size == 3 =>
                      Some((binSizes, binMinima))
                    case _ => None
                  }
                }
            }
            .left
            .map(_.asException)
            .toTry
            .flatMap {
              case Some(params) => Success(params)
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
