package uk.ac.wellcome.platform.stacks.common.models

import scala.util.{Failure, Success, Try}

sealed trait Identifier[T] {
  val value: T

  override def toString: String = value.toString
}

case class StacksWorkIdentifier(value: String) extends Identifier[String]
case class StacksUserIdentifier(value: String) extends Identifier[String]

sealed trait ItemIdentifier[T] extends Identifier[T]

case class CatalogueItemIdentifier(value: String) extends ItemIdentifier[String]
case class SierraItemIdentifier(value: Long) extends ItemIdentifier[Long]

object SierraItemIdentifier {
  def createFromSierraId(id: String): SierraItemIdentifier =
    Try {
      // This value looks like a URI when provided by Sierra
      // https://libsys.wellcomelibrary.org/iii/sierra-api/v5/patrons/holds/145730
      id.split("/").last.toLong
    } match {
      case Success(v) => SierraItemIdentifier(v)
      case Failure(e) =>
        throw new Exception("Failed to create SierraItemIdentifier", e)
    }
}

case class StacksItemIdentifier(
  catalogueId: CatalogueItemIdentifier,
  sierraId: SierraItemIdentifier
) extends ItemIdentifier[String] {
  override val value: String = catalogueId.value

  override def toString: String =
    s"<StacksItemIdentifier catalogue=$catalogueId, sierra=$sierraId>"
}
