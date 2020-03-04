package uk.ac.wellcome.platform.idminter.steps

import grizzled.slf4j.Logging
import io.circe.Json
import io.circe.optics.JsonPath.root
import uk.ac.wellcome.json.JsonUtil.fromJson
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal.SourceIdentifier

import scala.util.{Failure, Success, Try}

object SourceIdentifierScanner extends Logging{
  def scan(inputJson: Json): Try[List[SourceIdentifier]] =
    Try(iterate(root.each.json.getAll(inputJson), findIdentifier(root.sourceIdentifier.json.getOption(inputJson)).toList))

  private def iterate(children: List[Json], identifiers: List[SourceIdentifier]): List[SourceIdentifier] = {
    identifiers ++ children.foldRight(List[SourceIdentifier]())((j, idAccumulator) =>idAccumulator ++ iterate(root.each.json.getAll(j), findIdentifier(root.sourceIdentifier.json.getOption(j)).toList))
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
