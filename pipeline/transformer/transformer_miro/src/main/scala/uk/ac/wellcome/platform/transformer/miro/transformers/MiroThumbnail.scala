package uk.ac.wellcome.platform.transformer.miro.transformers

import uk.ac.wellcome.models.work.internal.{DigitalLocation, LocationType}
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord

trait MiroThumbnail extends MiroImages with MiroLicenses {
  def getThumbnail(miroRecord: MiroRecord): DigitalLocation =
    DigitalLocation(
      locationType = LocationType("thumbnail-image"),
      url = buildImageApiURL(
        miroId = miroRecord.imageNumber,
        templateName = "thumbnail"),
      imageSourceIds = List(getImageSourceId(miroRecord.imageNumber)),
      license = Some(
        chooseLicense(
          miroId = miroRecord.imageNumber,
          maybeUseRestrictions = miroRecord.useRestrictions
        )
      )
    )
}
