package uk.ac.wellcome.platform.transformer.sierra.data

import uk.ac.wellcome.models.work.internal.WorkType
import uk.ac.wellcome.platform.transformer.sierra.exceptions.SierraTransformerException
import uk.ac.wellcome.json.JsonUtil._

import scala.io.Source
import io.circe.parser.decode

object SierraMaterialTypes {

  sealed trait WorkTypeDescription
  case class Unlinked(label: String) extends WorkTypeDescription
  case class Linked(label: String, linkTo: String) extends WorkTypeDescription

  private lazy val materialTypesJson =
    Source
      .fromInputStream(getClass.getResourceAsStream("/sierra-material-types.json"))
      .getLines
      .mkString

  private lazy val workTypeDescriptions =
    decode[Map[String, WorkTypeDescription]](materialTypesJson) match {
      case Left(error) => throw error
      case Right(mapping) => mapping
        .map { case (key, value) => (key.toList -> value) }
        .collect { case (char :: Nil, value) => (char -> value) }
    }

  private lazy val unlinkedWorkTypes = workTypeDescriptions
    .collect {
      case (id, Unlinked(label)) =>
        id -> WorkType(id = id.toString, label = label)
    }

  private lazy val linkedWorkTypes = workTypeDescriptions
    .collect { case (id, Linked(_, linksTo)) => id -> linksTo.head }
    .flatMap {
      case (id, linksTo) => unlinkedWorkTypes.get(linksTo) match {
        case Some(workType) => Some(id -> workType)
        case None           => None
      }
    }

  private lazy val workTypeMap = unlinkedWorkTypes ++ linkedWorkTypes

  def fromCode(code: String): WorkType = {
    code.toList match {
      case List(c) =>
        workTypeMap.get(c) match {
          case Some(workType) => workType
          case None =>
            throw SierraTransformerException(s"Unrecognised work type code: $c")
        }
      case _ =>
        throw SierraTransformerException(
          s"Work type code is not a single character: <<$code>>")
    }
  }
}
