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
  def featureInferrerDeterministic(seed: Int): HttpResponse = json(
    s"""{
      "features_b64": "${Encoding.toLittleEndianBase64(
         randomFeatureVector(seed))}",
      "lsh_encoded_features": [${randomLshVector(seed)
         .map(str => s""""${str}"""")
         .mkString(", ")}]
    }""".stripMargin
  )

  def featureInferrer: HttpResponse =
    featureInferrerDeterministic(Random.nextInt())

  def paletteInferrerDeterministic(seed: Int): HttpResponse = json(
    s"""{
       "palette": [${randomPaletteVector(seed)
      .map(str => s""""${str}"""")
      .mkString(", ")}]
       }"""
  )

  def paletteInferrer: HttpResponse =
    paletteInferrerDeterministic(Random.nextInt())

  def randomPaletteVector(seed: Int): List[String] =
    List.fill(25)(List.fill(3)(new Random(seed).nextInt(10)).mkString(""))

  def randomFeatureVector(seed: Int): List[Float] =
    List.fill(4096)(new Random(seed).nextFloat)

  def randomLshVector(seed: Int): List[String] = {
    val random = new Random(seed)
    List.fill(256)(s"${random.nextInt(256)}-${random.nextInt(32)}")
  }

  def json(json: String): HttpResponse =
    HttpResponse(
      status = StatusCodes.OK,
      entity = HttpEntity.apply(
        contentType = ContentTypes.`application/json`,
        string = json
      )
    )

  def randomImageBytes(random: Random = Random): Array[Byte] = {
    val arr = Array.fill(32)(0x00.toByte)
    random.nextBytes(arr)
    arr
  }

  def image: HttpResponse =
    HttpResponse(
      status = StatusCodes.OK,
      entity = HttpEntity.apply(
        contentType = ContentType(MediaTypes.`image/jpeg`),
        bytes = randomImageBytes()
      )
    )

  def badRequest: HttpResponse =
    HttpResponse(status = StatusCodes.BadRequest)

  def serverError: HttpResponse =
    HttpResponse(status = StatusCodes.InternalServerError)
}
