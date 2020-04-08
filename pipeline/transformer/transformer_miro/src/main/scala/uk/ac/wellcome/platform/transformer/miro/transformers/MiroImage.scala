package uk.ac.wellcome.platform.transformer.miro.transformers

import uk.ac.wellcome.models.work.internal.{DigitalLocation, Identifiable, IdentifierType, LocationType, SourceIdentifier, UnmergedImage}
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord

trait MiroImage {
  private val imageUriTemplates = Map(
    "thumbnail" -> "%s/image/%s.jpg/full/300,/0/default.jpg",
    "info" -> "%s/image/%s.jpg/info.json"
  )

  def buildImageApiURL(miroId: String, templateName: String): String = {
    val iiifImageApiBaseUri = "https://iiif.wellcomecollection.org"
    val imageUriTemplate = imageUriTemplates.getOrElse(
      templateName,
      throw new Exception(
        s"Unrecognised Image API URI template ($templateName)!"))

    imageUriTemplate.format(iiifImageApiBaseUri, miroId)
  }

  def getImage(miroRecord: MiroRecord): UnmergedImage[Identifiable] =
    UnmergedImage(
      sourceIdentifier = SourceIdentifier(
        identifierType = IdentifierType("miro-image-number"),
        ontologyType = "Image",
        value = miroRecord.imageNumber
      ),
      location = DigitalLocation(
        url = buildImageApiURL(miroRecord.imageNumber, "info"),
        locationType = LocationType("iiif-image")
      )
    )
}
