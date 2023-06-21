package weco.pipeline.inference_manager.fixtures

import akka.http.scaladsl.model.{ContentType, ContentTypes, HttpEntity, HttpResponse, MediaTypes, StatusCodes}
import weco.catalogue.internal_model.generators.VectorGenerators

object Responses extends VectorGenerators {

  def featureInferrerDeterministic(seed: Int): HttpResponse = json(
    s"""{
      "features_b64": "${Encoding.toLittleEndianBase64(
         randomUnitLengthVector(seed).toList
       )}",
      "reduced_features_b64": "${Encoding.toLittleEndianBase64(
      randomUnitLengthVector(seed).toList.slice(0, 1024)
      )
    }"}""".stripMargin
  )

  def featureInferrer: HttpResponse =
    featureInferrerDeterministic(randomInt())

  def aspectRatioInferrerDeterministic(seed: Int): HttpResponse = json(
    s"""{
      "aspect_ratio": "${randomAspectRatio(seed).toString()}"
    }"""
  )

  def aspectRatioInferrer: HttpResponse =
    aspectRatioInferrerDeterministic(randomInt())

  def randomAspectRatio(seed: Int): Float = seed.toFloat

  def paletteInferrerDeterministic(seed: Int): HttpResponse = json(
    s"""{
       "paletteEmbedding": "${Encoding.toLittleEndianBase64(
        randomUnitLengthVector(seed).toList
      )}",
       "average_color_hex": "${randomAverageColorHex(seed)}",
     }"""
  )

  def paletteInferrer: HttpResponse =
    paletteInferrerDeterministic(randomInt())

  def randomAverageColorHex(seed: Int): String =
    s"#${randomBytes(seed).mkString.slice(0, 2)}"

  def json(json: String): HttpResponse =
    HttpResponse(
      status = StatusCodes.OK,
      entity = HttpEntity.apply(
        contentType = ContentTypes.`application/json`,
        string = json
      )
    )

  def image: HttpResponse =
    HttpResponse(
      status = StatusCodes.OK,
      entity = HttpEntity.apply(
        contentType = ContentType(MediaTypes.`image/jpeg`),
        bytes = randomBytes(32)
      )
    )

  def badRequest: HttpResponse =
    HttpResponse(status = StatusCodes.BadRequest)

  def serverError: HttpResponse =
    HttpResponse(status = StatusCodes.InternalServerError)
}
