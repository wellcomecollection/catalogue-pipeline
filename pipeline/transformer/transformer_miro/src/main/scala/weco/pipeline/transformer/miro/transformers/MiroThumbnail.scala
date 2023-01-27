package weco.pipeline.transformer.miro.transformers

import weco.catalogue.internal_model.locations.{DigitalLocation, LocationType}
import weco.catalogue.source_model.miro.MiroSourceOverrides
import weco.pipeline.transformer.miro.source.MiroRecord

trait MiroThumbnail extends MiroImageData with MiroLicenses {
  def getThumbnail(
    miroRecord: MiroRecord,
    overrides: MiroSourceOverrides
  ): DigitalLocation =
    DigitalLocation(
      locationType = LocationType.ThumbnailImage,
      url = buildImageApiURL(
        miroId = miroRecord.imageNumber,
        templateName = "thumbnail"
      ),
      license = Some(
        chooseLicense(
          maybeUseRestrictions = miroRecord.useRestrictions,
          overrides = overrides
        )
      )
    )
}
