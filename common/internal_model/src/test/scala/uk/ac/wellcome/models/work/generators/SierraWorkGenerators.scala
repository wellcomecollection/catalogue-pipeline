package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.models.work.internal._
import WorkState._

trait SierraWorkGenerators extends WorkGenerators with ItemsGenerators {

  def sierraSourceWork(): Work.Visible[Source] =
    sourceWork(sourceIdentifier = createSierraSystemSourceIdentifier)
      .otherIdentifiers(
        List(createSierraSystemSourceIdentifier)
      )

  def sierraIdentifiedWork(): Work.Visible[Identified] =
    identifiedWork(sourceIdentifier = createSierraSystemSourceIdentifier)
      .otherIdentifiers(
        List(createSierraSystemSourceIdentifier)
      )

  def sierraPhysicalSourceWork(): Work.Visible[Source] =
    sierraSourceWork().items(List(createPhysicalItem))

  def sierraDigitalSourceWork(): Work.Visible[Source] =
    sierraSourceWork().items(
      List(
        createUnidentifiableItemWith(locations = List(createDigitalLocation))
      )
    )
}
