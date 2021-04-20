package uk.ac.wellcome.platform.stacks.common.fixtures

import uk.ac.wellcome.platform.stacks.common.models.{
  CatalogueItemIdentifier,
  SierraItemIdentifier,
  StacksItemIdentifier,
  StacksWorkIdentifier
}

import scala.util.Random

trait IdentifierGenerators {
  private def randomAlphanumeric: String =
    Random.nextString(8)

  def maybe[T](t: T): Option[T] =
    if (Random.nextBoolean()) Some(t) else None

  def createStacksWorkIdentifier: StacksWorkIdentifier =
    StacksWorkIdentifier(s"work-$randomAlphanumeric")

  // Sierra identifiers are 7-digit numbers
  def createSierraItemIdentifier: SierraItemIdentifier =
    SierraItemIdentifier(
      (Random.nextFloat * 1000000).toLong
    )

  def createStacksItemIdentifier: StacksItemIdentifier =
    StacksItemIdentifier(
      catalogueId = CatalogueItemIdentifier(s"catalogue-$randomAlphanumeric"),
      sierraId = createSierraItemIdentifier
    )
}
