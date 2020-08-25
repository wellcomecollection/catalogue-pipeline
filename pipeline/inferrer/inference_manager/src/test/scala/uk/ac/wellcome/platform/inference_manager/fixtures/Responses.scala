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
  def featureInferrer: HttpResponse = json(
    s"""{
      "features_b64": "${Encoding.toLittleEndianBase64(randomFeatureVector)}",
      "lsh_encoded_features": [${randomLshVector
         .map(str => s""""${str}"""")
         .mkString(", ")}]
    }""".stripMargin
  )

  def paletteInferrer: HttpResponse = json(
    s"""{
       "palette": [${randomPaletteVector
      .map(str => s""""${str}"""")
      .mkString(", ")}]
       }"""
  )

  def randomPaletteVector: List[String] =
    List.fill(25)(List.fill(3)(Random.nextInt(10)).mkString(""))

  def randomFeatureVector: List[Float] = List.fill(4096)(Random.nextFloat)

  def randomLshVector: List[String] =
    List.fill(256)(s"${Random.nextInt(256)}-${Random.nextInt(32)}")

  def json(json: String): HttpResponse =
    HttpResponse(
      status = StatusCodes.OK,
      entity = HttpEntity.apply(
        contentType = ContentTypes.`application/json`,
        string = json
      )
    )

  def randomImageBytes: Array[Byte] = {
    val arr = Array.fill(32)(0x00.toByte)
    Random.nextBytes(arr)
    arr
  }

  def image: HttpResponse =
    HttpResponse(
      status = StatusCodes.OK,
      entity = HttpEntity.apply(
        contentType = ContentType(MediaTypes.`image/jpeg`),
        bytes = randomImageBytes
      )
    )

  def badRequest: HttpResponse =
    HttpResponse(status = StatusCodes.BadRequest)

  def serverError: HttpResponse =
    HttpResponse(status = StatusCodes.InternalServerError)
}
