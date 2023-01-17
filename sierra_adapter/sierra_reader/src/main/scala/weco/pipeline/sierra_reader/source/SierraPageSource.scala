package weco.pipeline.sierra_reader.source

import akka.NotUsed
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.{HttpEntity, StatusCodes}
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import grizzled.slf4j.Logging
import io.circe.Json
import io.circe.optics.JsonPath.root
import weco.catalogue.source_model.json.JsonOps._
import weco.http.client.HttpGet
import weco.http.json.CirceMarshalling
import weco.sierra.models.identifiers.SierraRecordTypes

import scala.concurrent.{ExecutionContext, Future}

class SierraPageSource(
  client: HttpGet
)(
  implicit
  ec: ExecutionContext,
  mat: Materializer
) extends Logging {
  implicit val um: Unmarshaller[HttpEntity, Json] =
    CirceMarshalling.fromDecoder[Json]

  private val lastId: Option[Int] = None

  def apply(
    recordType: SierraRecordTypes.Value,
    params: Map[String, String]
  ): Source[List[Json], NotUsed] =
    Source.unfoldAsync(lastId) { lastId =>
      val path = Path(recordType.toString)

      val newParams = lastId match {
        case Some(id) => params + ("id" -> s"[${id + 1},]")
        case None     => params
      }

      logger.debug(s"Making request to $path with parameters $newParams")

      for {
        nextPage <- client.get(path = path, params = newParams)

        jsonList <- nextPage match {
          case resp if resp.status == StatusCodes.OK =>
            Unmarshal(resp).to[Json].map { responseJson =>
              val records = root.entries.each.json.getAll(responseJson)
              val lastId = getLastId(records)

              Some((Some(lastId), records))
            }

          case resp if resp.status == StatusCodes.NotFound =>
            Future.successful(None)

          case resp =>
            Future.failed(new Throwable(s"Unexpected HTTP response: $resp"))
        }
      } yield jsonList
    }

  // The Sierra API returns entries as a list of the form:
  //
  //    [
  //      {"id": "1001", …},
  //      {"id": "1002", …},
  //      …
  //    ]
  //
  // This function returns the last ID in the list, which can be passed to the Sierra
  // API on a subsequent response "everything after this ID please".
  //
  // This isn't completely trivial -- bibs and items return the ID as a string, whereas
  // holdings return it as an int.
  //
  private def getLastId(entries: List[Json]): Int = {
    root.id
      .as[StringOrInt]
      .getOption(entries.last)
      .getOrElse(
        throw new RuntimeException(
          "Couldn't find ID in last item of list response")
      )
      .underlying
      .toInt
  }

}
