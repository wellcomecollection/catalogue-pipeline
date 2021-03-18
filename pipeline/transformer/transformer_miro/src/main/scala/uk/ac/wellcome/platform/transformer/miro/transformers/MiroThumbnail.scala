package uk.ac.wellcome.platform.transformer.miro.transformers

import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord
import weco.catalogue.internal_model.locations.{DigitalLocation, LocationType}

trait MiroThumbnail extends MiroImageData with MiroLicenses {
  def getThumbnail(miroRecord: MiroRecord): DigitalLocation =
    DigitalLocation(
      locationType = LocationType.ThumbnailImage,
      url = buildImageApiURL(
        miroId = miroRecord.imageNumber,
        templateName = "thumbnail"),
      license = Some(
        chooseLicense(
          miroId = miroRecord.imageNumber,
          maybeUseRestrictions = miroRecord.useRestrictions
        )
      )
    )
}
