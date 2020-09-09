package uk.ac.wellcome.platform.transformer.miro.transformers

import uk.ac.wellcome.models.work.internal.{
  DigitalLocationDeprecated,
  LocationType
}
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord

trait MiroThumbnail extends MiroImage with MiroLicenses {
  def getThumbnail(miroRecord: MiroRecord): DigitalLocationDeprecated =
    DigitalLocationDeprecated(
      locationType = LocationType("thumbnail-image"),
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
