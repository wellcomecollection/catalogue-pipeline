package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.models.work.internal._
import WorkState._
import weco.catalogue.internal_model.identifiers.IdState

trait SierraWorkGenerators extends WorkGenerators with ItemsGenerators {

  def sierraIdentifiedWork(): Work.Visible[Identified] =
    identifiedWork(sourceIdentifier = createSierraSystemSourceIdentifier)
      .otherIdentifiers(
        List(createSierraSystemSourceIdentifier)
      )

  def sierraIdentifiedWorkPair()
    : (Work.Visible[Identified], Work.Visible[Identified]) = {
    val digitisedWork = sierraDigitalIdentifiedWork()
    val physicalWork = sierraPhysicalIdentifiedWork()
      .mergeCandidates(
        List(
          MergeCandidate(
            id = IdState.Identified(
              digitisedWork.state.canonicalId,
              digitisedWork.sourceIdentifier),
            reason = Some("Physical/digitised Sierra work")
          )
        )
      )

    (digitisedWork, physicalWork)
  }

  def sierraPhysicalIdentifiedWork(): Work.Visible[Identified] =
    sierraIdentifiedWork().items(List(createIdentifiedPhysicalItem))

  def sierraDigitalIdentifiedWork(): Work.Visible[Identified] =
    sierraIdentifiedWork().items(
      List(
        createUnidentifiableItemWith(locations = List(createDigitalLocation))
      )
    )
}
