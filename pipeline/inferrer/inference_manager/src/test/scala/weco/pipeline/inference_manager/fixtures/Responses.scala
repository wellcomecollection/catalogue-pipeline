package weco.pipeline.inference_manager.fixtures

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
      randomFeatureVector(seed)
    )}",
      "reduced_features_b64": "${Encoding.toLittleEndianBase64(
      randomFeatureVector(seed).slice(0, 1024)
    )
    }"}""".stripMargin
  )

  def featureInferrer: HttpResponse =
    featureInferrerDeterministic(Random.nextInt())

  def aspectRatioInferrerDeterministic(seed: Int): HttpResponse = json(
    s"""{
      "aspect_ratio": "${randomAspectRatio(seed).toString()}"
    }"""
  )

  def aspectRatioInferrer: HttpResponse =
    aspectRatioInferrerDeterministic(Random.nextInt())

  def randomAspectRatio(seed: Int): Float = new Random(seed).nextFloat()

  def paletteInferrerDeterministic(seed: Int): HttpResponse = json(
    s"""{
       "palette": [${Encoding.toLittleEndianBase64(randomPaletteVector(seed))
      .map(str => s""""$str"""")
      .mkString(", ")}],
       "average_color_hex": "${randomAverageColorHex(seed)}",
     }"""
  )

  def paletteInferrer: HttpResponse =
    paletteInferrerDeterministic(Random.nextInt())

  def randomPaletteVector(seed: Int): List[Float] =
    List.fill(216)(new Random(seed).nextGaussian().toFloat)

  def randomAverageColorHex(seed: Int): String =
    s"#${randomBytes(random = new Random(seed), length = 3).map(b => f"$b%02X").mkString}"

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

  def randomBytes(random: Random = Random, length: Int = 32): Array[Byte] = {
    val arr = Array.fill(length)(0x00.toByte)
    random.nextBytes(arr)
    arr
  }

  def image: HttpResponse =
    HttpResponse(
      status = StatusCodes.OK,
      entity = HttpEntity.apply(
        contentType = ContentType(MediaTypes.`image/jpeg`),
        bytes = randomBytes()
      )
    )

  def badRequest: HttpResponse =
    HttpResponse(status = StatusCodes.BadRequest)

  def serverError: HttpResponse =
    HttpResponse(status = StatusCodes.InternalServerError)
}
