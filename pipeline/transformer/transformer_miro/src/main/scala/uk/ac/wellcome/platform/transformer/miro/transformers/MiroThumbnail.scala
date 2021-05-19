package uk.ac.wellcome.platform.transformer.miro.transformers

import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord
import weco.catalogue.internal_model.locations.{DigitalLocation, LocationType}
import weco.catalogue.source_model.miro.MiroSourceOverrides

trait MiroThumbnail extends MiroImageData with MiroLicenses {
  def getThumbnail(miroRecord: MiroRecord, overrides: MiroSourceOverrides): DigitalLocation =
    DigitalLocation(
      locationType = LocationType.ThumbnailImage,
      url = buildImageApiURL(
        miroId = miroRecord.imageNumber,
        templateName = "thumbnail"),
      license = Some(
        chooseLicense(
          maybeUseRestrictions = miroRecord.useRestrictions,
          overrides = overrides
        )
      )
    )
}
