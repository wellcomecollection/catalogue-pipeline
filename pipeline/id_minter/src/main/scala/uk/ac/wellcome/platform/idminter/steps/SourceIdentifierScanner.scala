package uk.ac.wellcome.platform.idminter.steps

import grizzled.slf4j.Logging
import io.circe.Json
import io.circe.optics.JsonPath.root
import uk.ac.wellcome.json.JsonUtil.fromJson
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal.SourceIdentifier
import uk.ac.wellcome.platform.idminter.models.Identifier

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

object SourceIdentifierScanner extends Logging {
  def update(inputJson: Json, identifiers: Map[SourceIdentifier, Identifier]): Try[Json] =
    Try {
      root.sourceIdentifier.json.getOption(inputJson) match {
        case Some(sourceIdentifierJson) =>
          val sourceIdentifier = parseSourceIdentifier(sourceIdentifierJson)
          identifiers.get(sourceIdentifier) match {
            case Some(identifier) =>
              root.obj.modify(json => ("canonicalId", Json.fromString(identifier.CanonicalId)) +: json)(inputJson)
            case None => ???
          }

      case None => ???
    }
}



  def scan(inputJson: Json): Try[List[SourceIdentifier]] =
    Try(iterate(root.each.json.getAll(inputJson), findIdentifier(root.sourceIdentifier.json.getOption(inputJson)).toList))

  @tailrec
  private def iterate(
                       children: List[Json],
                       identifiers: List[SourceIdentifier]): List[SourceIdentifier] =
    children match {
      case Nil => identifiers
      case headChild :: tailChildren =>
        iterate(
          root.each.json.getAll(headChild) ++ tailChildren,
          identifiers ++ findIdentifier(
            root.sourceIdentifier.json.getOption(headChild)
          ).toList
        )
    }

  private def findIdentifier(json: Option[Json]): Option[SourceIdentifier] =
    json match {
      case Some(sourceIdentifierJson) =>
        val sourceIdentifier = parseSourceIdentifier(sourceIdentifierJson)
        Some(sourceIdentifier)

      case None => None
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
