package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.models.work.internal.{Work, WorkState}

trait MetsWorkGenerators extends WorkGenerators with ImageGenerators {
  def metsSourceWork(): Work.Visible[WorkState.Source] =
    sourceWork(sourceIdentifier = createMetsSourceIdentifier)
      .items(List(createDigitalItem))
      .images(List(createUnmergedMetsImage))
}
