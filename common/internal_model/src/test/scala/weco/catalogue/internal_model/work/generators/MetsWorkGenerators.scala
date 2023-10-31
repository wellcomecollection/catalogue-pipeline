package weco.catalogue.internal_model.work.generators

import weco.catalogue.internal_model.generators.ImageGenerators
import weco.catalogue.internal_model.locations.License
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.work.{Work, WorkState}

trait MetsWorkGenerators extends WorkGenerators with ImageGenerators {
  def metsIdentifiedWork(): Work.Visible[WorkState.Identified] =
    identifiedWork(sourceIdentifier = createMetsSourceIdentifier)
      .items(List(createDigitalItem))
      .imageData(List(createMetsImageData.toIdentified))

  def createInvisibleMetsIdentifiedWorkWith(
    numImages: Int,
    imageLocationLicence: Option[License] = Some(License.CCBY)
  ): Work.Invisible[Identified] = {
    val images =
      (1 to numImages).map {
        _ =>
          createMetsImageDataWith(locationLicence =
            imageLocationLicence
          ).toIdentified
      }.toList

    metsIdentifiedWork().imageData(images).invisible()
  }

}
