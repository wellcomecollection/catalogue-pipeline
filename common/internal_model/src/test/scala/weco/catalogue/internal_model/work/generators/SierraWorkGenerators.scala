package weco.catalogue.internal_model.work.generators

import weco.catalogue.internal_model.identifiers.{IdState, IdentifierType}
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

  def sierraEbscoIdentifiedWorkPair(): (
    Work.Visible[WorkState.Identified],
    Work.Visible[WorkState.Identified]
  ) = {
    val ebscoWork = ebscoIdentifiedWork()
    val sierraDigitalWork = sierraDigitalIdentifiedWork()
      .mergeCandidates(
        List(
          MergeCandidate(
            id = IdState.Identified(
              canonicalId = ebscoWork.state.canonicalId,
              sourceIdentifier = ebscoWork.sourceIdentifier
            ),
            reason = "Ebsco/Sierra work"
          )
        )
      )

    (sierraDigitalWork, ebscoWork)
  }

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

  def ebscoIdentifiedWork(): Work.Visible[WorkState.Identified] =
    identifiedWork(sourceIdentifier =
      createSourceIdentifierWith(IdentifierType.EbscoAltLookup)
    )

  def sierraPhysicalIdentifiedWork(): Work.Visible[WorkState.Identified] =
    sierraIdentifiedWork().items(List(createIdentifiedPhysicalItem))

  def sierraDigitalIdentifiedWork(): Work.Visible[WorkState.Identified] =
    sierraIdentifiedWork().items(
      List(
        createUnidentifiableItemWith(locations = List(createDigitalLocation))
      )
    )
}
