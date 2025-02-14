package weco.pipeline.id_minter

import io.circe.Json
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.json.JsonUtil.fromJson
import weco.pipeline.id_minter.steps.{
  CanonicalIdentifierGenerator,
  SourceIdentifierEmbedder
}
import weco.catalogue.internal_model.Implicits._

import scala.util.Try

class SingleDocumentIdMinter(
  identifierGenerator: CanonicalIdentifierGenerator
) {

  def processJson(json: Json): Try[Work[Identified]] = {
    for {
      updatedJson <- embedIds(json)
      work <- decodeWork(updatedJson)
    } yield work
  }

  private def embedIds(json: Json): Try[Json] =
    for {
      sourceIdentifiers <- SourceIdentifierEmbedder.scan(json)
      mintedIdentifiers <- identifierGenerator.retrieveOrGenerateCanonicalIds(
        sourceIdentifiers
      )

      canonicalIdentifiers = mintedIdentifiers.map {
        case (sourceIdentifier, identifier) =>
          (sourceIdentifier, identifier.CanonicalId)
      }

      updatedJson <- SourceIdentifierEmbedder.update(json, canonicalIdentifiers)
    } yield updatedJson

  private def decodeWork(json: Json): Try[Work[Identified]] =
    fromJson[Work[Identified]](json.noSpaces)
}
