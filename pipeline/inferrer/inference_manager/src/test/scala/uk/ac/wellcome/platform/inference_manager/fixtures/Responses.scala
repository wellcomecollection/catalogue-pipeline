package uk.ac.wellcome.platform.inference_manager.fixtures

import akka.http.scaladsl.model.{
  ContentType,
  ContentTypes,
  HttpEntity,
  HttpResponse,
  MediaTypes,
  StatusCodes
}

import scala.util.Random

object Responses {
  def featureInferrerResponse: HttpResponse = jsonResponse(
    s"""{
      "features_b64": "${Encoding.toLittleEndianBase64(randomFeatureVector)}",
      "lsh_encoded_features": [${randomLshVector
         .map(str => s""""${str}"""")
         .mkString(", ")}]
    }""".stripMargin
  )

  def randomFeatureVector: List[Float] = List.fill(4096)(Random.nextFloat)

  def randomLshVector: List[String] =
    List.fill(256)(s"${Random.nextInt(256)}-${Random.nextInt(32)}")

  def jsonResponse(json: String): HttpResponse =
    HttpResponse(
      status = StatusCodes.OK,
      entity = HttpEntity.apply(
        contentType = ContentTypes.`application/json`,
        string = json
      )
    )

  def imageResponse: HttpResponse =
    HttpResponse(
      status = StatusCodes.OK,
      entity = HttpEntity.apply(
        contentType = ContentType(MediaTypes.`image/jpeg`),
        bytes = Array.fill(32)(0xFF.toByte)
      )
    )

  def badRequestResponse: HttpResponse =
    HttpResponse(status = StatusCodes.BadRequest)

  def serverErrorResponse: HttpResponse =
    HttpResponse(status = StatusCodes.InternalServerError)
}
