package weco.pipeline.id_minter.steps

import grizzled.slf4j.Logging
import io.circe.Json
import io.circe.optics.JsonPath.root
import io.circe.optics.JsonOptics._
import monocle.function.Plated
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.identifiers.{CanonicalId, SourceIdentifier}
import weco.pipeline.id_minter.models.Identifier

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

object SourceIdentifierEmbedder extends Logging {

  /** Find all the source identifiers within this JSON.
    *
    * It looks at every object in the JSON and looks for a "sourceIdentifier" key
    * which contains a SourceIdentifier object.
    *
    */
  def scan(inputJson: Json): Try[List[SourceIdentifier]] =
    Try(
      iterate(
        root.each.json.getAll(inputJson),
        root.sourceIdentifier.json
          .getOption(inputJson)
          .map(parseSourceIdentifier)
          .toList
      )
    )

  /** Updates a JSON with the minted identifiers.
    *
    * In particular:
    *
    *   - It looks at every object in the JSON.  If it has a "sourceIdentifier" key,
    *     it adds an "canonicalId" with the corresponding canonical ID.
    *
    *   - It renamed the "identifiedType" key to "type".  The latter is used by Circe as
    *     a type discriminator, so now Circe will decode this value as the identified version.
    *
    * e.g. you might have the Identifiable JSON:
    *
    *     {
    *       "sourceIdentifier" : {
    *         "identifierType" : {"id" : "lc-subjects"},
    *         "ontologyType" : "Subject",
    *         "value" : "sh85002427"
    *       },
    *       "identifiedType" : "Identified",
    *       "type" : "Identifiable"
    *     }
    *
    * Circe would decode this as type "IdState.Identifiable".  Once it passes through this method,
    * it becomes
    *
    *     {
    *       "canonicalId": "jsywryz7",
    *       "sourceIdentifier" : {
    *         "identifierType" : {"id" : "lc-subjects"},
    *         "ontologyType" : "Subject",
    *         "value" : "sh85002427"
    *       },
    *       "type" : "Identified"
    *     }
    *
    * which now gets decoded as "IdState.Identified".
    *
    */
  def update(inputJson: Json,
             identifiers: Map[SourceIdentifier, Identifier]): Try[Json] =
    Try {
      val updateNode =
        (updateNodeType _) compose addCanonicalIdToNode(identifiers)
      // Plated transforms do not operate on the top-level node of
      // as structure, so we need to update the root separately
      val updatedRoot = updateNode(inputJson)
      Plated.transform[Json](updateNode)(updatedRoot)
    }

  private def updateNodeType(node: Json): Json =
    root.identifiedType.json
      .getOption(node)
      .flatMap(_.asString)
      .map { identifiedType =>
        root.obj.modify { obj =>
          ("type", Json.fromString(identifiedType)) +:
            obj.remove("identifiedType")
        }(node)
      }
      .getOrElse(node)

  private def addCanonicalIdToNode(
    identifiers: Map[SourceIdentifier, Identifier])(node: Json): Json =
    root.sourceIdentifier.json
      .getOption(node)
      .map(parseSourceIdentifier)
      .map(getCanonicalId(identifiers))
      .map { canonicalId =>
        root.obj.modify { obj =>
          ("canonicalId", Json.fromString(canonicalId.underlying)) +: obj
        }(node)
      }
      .getOrElse(node)

  private def getCanonicalId(identifiers: Map[SourceIdentifier, Identifier])(
    sourceIdentifier: SourceIdentifier): CanonicalId =
    identifiers
      .getOrElse(
        sourceIdentifier,
        throw new RuntimeException(
          s"Could not find $sourceIdentifier in $identifiers")
      )
      .CanonicalId

  @tailrec
  private def iterate(
    children: List[Json],
    identifiers: List[SourceIdentifier]): List[SourceIdentifier] =
    children match {
      case Nil => identifiers
      case headChild :: tailChildren =>
        iterate(
          root.each.json.getAll(headChild) ++ tailChildren,
          identifiers ++ root.sourceIdentifier.json
            .getOption(headChild)
            .map(parseSourceIdentifier)
        )
    }

  private def parseSourceIdentifier(
    sourceIdentifierJson: Json): SourceIdentifier = {
    sourceIdentifierJson.as[SourceIdentifier].toTry match {
      case Success(sourceIdentifier) => sourceIdentifier
      case Failure(exception) =>
        error(
          s"Error parsing source identifier: ${sourceIdentifierJson.spaces2}")
        throw exception
    }

  }

}
