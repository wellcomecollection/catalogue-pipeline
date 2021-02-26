package weco.catalogue.sierra_reader.source

import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import akka.stream.{Attributes, Outlet, SourceShape}
import io.circe.optics.JsonPath.root
import io.circe.parser.parse
import io.circe.{Decoder, Encoder, HCursor, Json}
import org.slf4j.{Logger, LoggerFactory}
import scalaj.http.{Http, HttpOptions, HttpResponse}
import uk.ac.wellcome.platform.sierra_reader.config.models.SierraAPIConfig

import scala.concurrent.duration.Duration

class SierraPageSource(
  config: SierraAPIConfig,
  timeout: Duration
)(
  resourceType: String,
  params: Map[String, String] = Map()
) extends GraphStage[SourceShape[List[Json]]] {

  val out: Outlet[List[Json]] = Outlet("SierraSource")

  override def shape = SourceShape(out)
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {

      var token: String = refreshToken(config)
      var lastId: Option[Int] = None
      var jsonList: List[Json] = Nil

      setHandler(out, new OutHandler {
        override def onPull(): Unit = makeSierraRequestAndPush()
      })

      private def makeSierraRequestAndPush(): Unit = {
        val newParams = lastId match {
          case Some(id) =>
            params + ("id" -> s"[${id + 1},]")
          case None => params
        }

        makeRequestWith(
          newParams,
          ifUnauthorized = {
            token = refreshToken(config)
            makeRequestWith(newParams, ifUnauthorized = {
              fail(out, new RuntimeException("Unable to refresh token!"))
            })
          }
        )
      }

      private def makeRequestWith[T](newParams: Map[String, String],
                                     ifUnauthorized: => Unit): Unit = {
        val newResponse =
          makeRequest(config.apiURL, resourceType, token, newParams)

        newResponse.code match {
          case 200 => refreshJsonListAndPush(newResponse)
          case 404 => complete(out)
          case 401 => ifUnauthorized
          case code =>
            fail(
              out,
              new RuntimeException(
                s"Unexpected HTTP status code from Sierra: $code"))
        }
      }

      private def refreshJsonListAndPush(
        response: HttpResponse[String]): Unit = {
        val responseJson = parse(response.body).right
          .getOrElse(
            throw new RuntimeException(
              s"List response was not JSON; got ${response.body}"))

        jsonList = root.entries.each.json.getAll(responseJson)

        lastId = Some(getLastId(jsonList))

        push(out, jsonList)
      }

      private def refreshToken(config: SierraAPIConfig): String = {
        val url = s"${config.apiURL}/token"

        val tokenResponse =
          Http(url).postForm.auth(config.oauthKey, config.oauthSec).asString

        val json = parse(tokenResponse.body).right
          .getOrElse(
            throw new RuntimeException(
              s"Token response was not JSON; got ${tokenResponse.body}"))
        root.access_token.string
          .getOption(json)
          .getOrElse(
            throw new Exception(
              s"Couldn't find access_token in token response; got ${tokenResponse.body}"
            )
          )
      }

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
  private class ResponseId(val underlying: Int)

  implicit private val decoder: Decoder[ResponseId] =
    (c: HCursor) => c.as[String] match {
      case Right(value) => Right(new ResponseId(value.toInt))
      case Left(_) => c.as[Int].map { v => new ResponseId(v) }
    }

  implicit private val encoder: Encoder[ResponseId] =
    (id: ResponseId) => Json.fromInt(id.underlying)

  private def getLastId(entries: List[Json]): Int = {
    root.id.as[ResponseId]
      .getOption(entries.last)
      .getOrElse(
        throw new RuntimeException("Couldn't find ID in last item of list response")
      )
      .underlying
  }

  private def makeRequest(apiUrl: String,
                          resourceType: String,
                          token: String,
                          params: Map[String, String]): HttpResponse[String] = {
    val url = s"$apiUrl/$resourceType"
    logger.debug(
      s"Making request to $url with parameters $params & token $token")

    Http(url)
      .option(HttpOptions.readTimeout(timeout.toMillis.toInt))
      .option(HttpOptions.connTimeout(timeout.toMillis.toInt))
      .params(params)
      .header("Authorization", s"Bearer $token")
      .header("Accept", "application/json")
      .header("Connection", "close")
      .asString
  }
}
