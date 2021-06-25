package weco.catalogue.internal_model.work.generators

import weco.catalogue.internal_model.generators.ImageGenerators
import weco.catalogue.internal_model.work.{Work, WorkState}

trait MetsWorkGenerators extends WorkGenerators with ImageGenerators {
  def metsIdentifiedWork(): Work.Visible[WorkState.Identified] =
    identifiedWork(sourceIdentifier = createMetsSourceIdentifier)
      .items(List(createDigitalItem))
      .imageData(List(createMetsImageData.toIdentified))
}
