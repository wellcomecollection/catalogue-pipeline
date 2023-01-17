package weco.catalogue.internal_model.work.generators

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{MergeCandidate, Work, WorkState}

trait SierraWorkGenerators extends WorkGenerators with ItemsGenerators {

  def sierraIdentifiedWork(): Work.Visible[WorkState.Identified] =
    identifiedWork(sourceIdentifier = createSierraSystemSourceIdentifier)
      .otherIdentifiers(
        List(
          createSierraSystemSourceIdentifier,
          createSierraIdentifierSourceIdentifier
        )
      )

  def sierraIdentifiedWorkPair(): (
    Work.Visible[WorkState.Identified],
    Work.Visible[WorkState.Identified]
  ) = {
    val digitisedWork = sierraDigitalIdentifiedWork()
    val physicalWork = sierraPhysicalIdentifiedWork()
      .mergeCandidates(
        List(
          MergeCandidate(
            id = IdState.Identified(
              canonicalId = digitisedWork.state.canonicalId,
              sourceIdentifier = digitisedWork.sourceIdentifier
            ),
            reason = "Physical/digitised Sierra work"
          )
        )
      )

    (digitisedWork, physicalWork)
  }

  def sierraPhysicalIdentifiedWork(): Work.Visible[WorkState.Identified] =
    sierraIdentifiedWork().items(List(createIdentifiedPhysicalItem))

  def sierraDigitalIdentifiedWork(): Work.Visible[WorkState.Identified] =
    sierraIdentifiedWork().items(
      List(
        createUnidentifiableItemWith(locations = List(createDigitalLocation))
      )
    )
}
