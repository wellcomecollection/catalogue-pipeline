package uk.ac.wellcome.platform.transformer.calm

import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.work.internal._
import grizzled.slf4j.Logging
import uk.ac.wellcome.platform.transformer.calm.exceptions.TransformerException
import uk.ac.wellcome.platform.transformer.calm.models.CalmSourceData
import uk.ac.wellcome.platform.transformer.calm.transformers.{
  CalmCollection,
  CalmFieldTransformer,
  CalmOtherIdentifiers,
  CalmSourceIdentifier,
  CalmTitle
}

object CalmTransformableTransformer {

  def transform(transformable: CalmSourceData)
    : Either[TransformerException, TransformedBaseWork] = {
    val t = Map(
      "title" -> CalmTitle,
      "collection" -> CalmCollection,
      "otherIdentifiers" -> CalmOtherIdentifiers,
      "sourceIdentifier" -> CalmSourceIdentifier
    ) map {
      case (key: String, transformer: CalmFieldTransformer) =>
        (key, transformer.transform(transformable))
    }

    val g = t.get("title")

    ???
  }

}
