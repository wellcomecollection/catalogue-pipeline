package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.models.work.internal.{Work, WorkState}
import weco.catalogue.internal_model.generators.ImageGenerators

trait MetsWorkGenerators extends WorkGenerators with ImageGenerators {
  def metsIdentifiedWork(): Work.Visible[WorkState.Identified] =
    identifiedWork(sourceIdentifier = createMetsSourceIdentifier)
      .items(List(createDigitalItem))
      .imageData(List(createMetsImageData.toIdentified))
}
