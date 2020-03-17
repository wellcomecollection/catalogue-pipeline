package uk.ac.wellcome.platform.idminter.steps

import grizzled.slf4j.Logging
import io.circe.Json
import io.circe.optics.JsonPath.root
import io.circe.optics.JsonOptics._
import monocle.function.Plated
import uk.ac.wellcome.json.JsonUtil.fromJson
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal.SourceIdentifier
import uk.ac.wellcome.platform.idminter.models.Identifier

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

object SourceIdentifierScanner extends Logging {
  def update(inputJson: Json,
             identifiers: Map[SourceIdentifier, Identifier]): Try[Json] =
    Try {
      // Plated transforms operate on self-similar *children*
      // so we need to update the root separately
      val updatedRoot = updateNode(identifiers)(inputJson)
      Plated.transform[Json](updateNode(identifiers))(updatedRoot)
    }

  private def updateNode(identifiers: Map[SourceIdentifier, Identifier])(
    node: Json): Json =
    root.sourceIdentifier.json
      .getOption(node)
      .map(parseSourceIdentifier)
      .map { sourceIdentifier =>
        identifiers.get(sourceIdentifier) match {
          case Some(Identifier(canonicalId, _, _, _)) =>
            root.obj.modify { identifierJson =>
              ("canonicalId", Json.fromString(canonicalId)) +: identifierJson
            }(node)
          case None =>
            throw new RuntimeException(
              s"Could not find $sourceIdentifier in $identifiers")
        }
      }
      .getOrElse(node)

  def scan(inputJson: Json): Try[List[SourceIdentifier]] =
    Try(
      scanIterate(
        root.each.json.getAll(inputJson),
        root.sourceIdentifier.json
          .getOption(inputJson)
          .map(parseSourceIdentifier)
          .toList
      )
    )

  @tailrec
  private def scanIterate(
    children: List[Json],
    identifiers: List[SourceIdentifier]): List[SourceIdentifier] =
    children match {
      case Nil => identifiers
      case headChild :: tailChildren =>
        scanIterate(
          root.each.json.getAll(headChild) ++ tailChildren,
          identifiers ++ root.sourceIdentifier.json
            .getOption(headChild)
            .map(parseSourceIdentifier)
        )
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
