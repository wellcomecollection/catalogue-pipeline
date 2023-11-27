package weco.pipeline.transformer.mets.transformers

import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.image.ImageData
import weco.catalogue.internal_model.locations.{
  DigitalLocation,
  License,
  LocationType
}
import weco.pipeline.transformer.mets.transformer.models.FileReference

object MetsImageData extends DLCSFilenameNormaliser {

  def apply(
    recordIdentifier: String,
    version: Int,
    license: Option[License],
    manifestLocation: DigitalLocation,
    fileReference: FileReference
  ): ImageData[IdState.Identifiable] = {
    val url = buildImageUrl(recordIdentifier, fileReference)
    ImageData[IdState.Identifiable](
      id = IdState.Identifiable(
        sourceIdentifier = getImageSourceId(recordIdentifier, fileReference.id)
      ),
      version = version,
      locations = List(
        DigitalLocation(
          url = url,
          locationType = LocationType.IIIFImageAPI,
          license = license,
          accessConditions = manifestLocation.accessConditions
        ),
        manifestLocation
      )
    )
  }

  private def getImageSourceId(
    bnumber: String,
    fileId: String
  ): SourceIdentifier =
    SourceIdentifier(
      identifierType = IdentifierType.METSImage,
      ontologyType = "Image",
      value = s"$bnumber/$fileId"
    )

  private def buildImageUrl(
    recordIdentifier: String,
    reference: FileReference
  ) =
    s"https://iiif.wellcomecollection.org/image/${normaliseLocation(recordIdentifier, reference.location)}/info.json"

}
