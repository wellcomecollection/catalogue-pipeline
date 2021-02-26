package weco.catalogue.sierra_reader.source

import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import akka.stream.{Attributes, Outlet, SourceShape}
import io.circe.Json
import io.circe.optics.JsonPath.root
import io.circe.parser.parse
import org.slf4j.{Logger, LoggerFactory}
import scalaj.http.{Http, HttpOptions, HttpResponse}

class SierraPageSource(
  apiUrl: String,
  oauthKey: String,
  oauthSecret: String,
  timeoutMs: Int
)(
  resourceType: String,
  params: Map[String, String] = Map()
) extends GraphStage[SourceShape[List[Json]]] {

  val out: Outlet[List[Json]] = Outlet("SierraSource")

  override def shape = SourceShape(out)
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {

      var token: String = refreshToken(apiUrl, oauthKey, oauthSecret)
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
            token = refreshToken(apiUrl, oauthKey, oauthSecret)
            makeRequestWith(newParams, ifUnauthorized = {
              fail(out, new RuntimeException("Unable to refresh token!"))
            })
          }
        )
      }

      private def makeRequestWith[T](newParams: Map[String, String],
                                     ifUnauthorized: => Unit): Unit = {
        val newResponse = makeRequest(apiUrl, resourceType, token, newParams)

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
          .getOrElse(throw new RuntimeException(s"List response was not JSON; got ${response.body}"))

        jsonList = root.entries.each.json.getAll(responseJson)

        lastId = Some(
          root.id.string
            .getOption(jsonList.last)
            .getOrElse(
              throw new RuntimeException(
                s"Couldn't find ID in last item of list response; got ${response.body}"
              )
            )
            .toInt)

        push(out, jsonList)
      }

      private def refreshToken(apiUrl: String,
                               oauthKey: String,
                               oauthSecret: String) = {
        val tokenResponse =
          Http(s"$apiUrl/token").postForm.auth(oauthKey, oauthSecret).asString
        val json = parse(tokenResponse.body).right
          .getOrElse(throw new RuntimeException(s"Token response was not JSON; got ${tokenResponse.body}"))
        root.access_token.string
          .getOption(json)
          .getOrElse(
            throw new Exception(
              s"Couldn't find access_token in token response; got ${tokenResponse.body}"
            )
          )
      }

    }

  private def makeRequest(apiUrl: String,
                          resourceType: String,
                          token: String,
                          params: Map[String, String]): HttpResponse[String] = {
    val url = s"$apiUrl/$resourceType"
    logger.debug(s"Making request to $url with parameters $params & token $token")

    Http(url)
      .option(HttpOptions.readTimeout(timeoutMs))
      .option(HttpOptions.connTimeout(timeoutMs))
      .params(params)
      .header("Authorization", s"Bearer $token")
      .header("Accept", "application/json")
      .header("Connection", "close")
      .asString
  }
}
