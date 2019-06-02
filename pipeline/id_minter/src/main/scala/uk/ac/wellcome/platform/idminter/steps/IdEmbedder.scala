package uk.ac.wellcome.platform.idminter.steps

import grizzled.slf4j.Logging
import io.circe._
import io.circe.optics.JsonPath.root
import io.circe.optics.JsonTraversalPath
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.work.internal.SourceIdentifier

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

class IdEmbedder(identifierGenerator: IdentifierGenerator) extends Logging {

  def embedId(json: Json): Try[Json] = Try {
    iterate(root.each, addIdentifierToJson(json))
  }

  @tailrec
  private def iterate(rootChildrenPath: JsonTraversalPath, json: Json): Json = {
    if (rootChildrenPath.json.nonEmpty(json)) {
      val newJson =
        rootChildrenPath.json.modify(addIdentifierToJson)(json)
      iterate(rootChildrenPath.each, newJson)
    } else json
  }

  private def addIdentifierToJson(json: Json): Json = {
    if (json.isObject) {
      generateAndAddCanonicalId(json)
    } else json
  }

  private def generateAndAddCanonicalId(json: Json): Json = {
    root.sourceIdentifier.json.getOption(json) match {
      case Some(sourceIdentifierJson) =>
        val sourceIdentifier = parseSourceIdentifier(sourceIdentifierJson)
        val result = identifierGenerator
          .retrieveOrGenerateCanonicalId(
            sourceIdentifier = sourceIdentifier
          )

        val canonicalId = result match {
          case Right(value)       => value
          case Left(storageError) => throw storageError.e
        }

        addCanonicalId(json, canonicalId)

      case None => json
    }
  }

  private def addCanonicalId(json: Json, canonicalId: String) = {
    root.identifiedType.json.getOption(json) match {
      case Some(identifiedType) =>
        root.obj.modify { obj =>
          ("type", identifiedType) +:
            ("canonicalId", Json.fromString(canonicalId)) +:
            obj.remove("identifiedType")
        }(json)
      case None =>
        root.obj.modify(obj =>
          ("canonicalId", Json.fromString(canonicalId)) +: obj)(json)
    }
  }

  private def parseSourceIdentifier(
    sourceIdentifierJson: Json): SourceIdentifier = {
    fromJson[SourceIdentifier](sourceIdentifierJson.toString()) match {
      case Success(sourceIdentifier) => sourceIdentifier
      case Failure(exception) =>
        error(s"Error parsing source identifier: $exception")
        throw exception
    }

  }
}
