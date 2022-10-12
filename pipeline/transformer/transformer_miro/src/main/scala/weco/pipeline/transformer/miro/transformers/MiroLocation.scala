package weco.pipeline.transformer.miro.transformers

import weco.catalogue.internal_model.locations._
import weco.catalogue.source_model.miro.MiroSourceOverrides
import weco.pipeline.transformer.miro.source.MiroRecord

trait MiroLocation extends MiroLicenses with MiroContributorCodes {

  private val imageUriTemplates = Map(
    "thumbnail" -> "%s/image/%s/full/300,/0/default.jpg",
    "info" -> "%s/image/%s/info.json"
  )

  def buildImageApiURL(miroId: String, templateName: String): String = {
    val iiifImageApiBaseUri = "https://iiif.wellcomecollection.org"
    val imageUriTemplate = imageUriTemplates.getOrElse(
      templateName,
      throw new Exception(
        s"Unrecognised Image API URI template ($templateName)!"))

    imageUriTemplate.format(iiifImageApiBaseUri, miroId)
  }

  def getLocation(miroRecord: MiroRecord,
                  overrides: MiroSourceOverrides): DigitalLocation =
    DigitalLocation(
      locationType = LocationType.IIIFImageAPI,
      url = buildImageApiURL(
        miroId = miroRecord.imageNumber,
        templateName = "info"
      ),
      credit = MiroContributorCredit.getCredit(miroRecord),
      license = Some(
        chooseLicense(
          maybeUseRestrictions = miroRecord.useRestrictions,
          overrides = overrides
        )
      ),
      accessConditions = List(
        AccessCondition(
          method = AccessMethod.ViewOnline,
          status = Some(AccessStatus.Open)
        )
      )
    )
}
