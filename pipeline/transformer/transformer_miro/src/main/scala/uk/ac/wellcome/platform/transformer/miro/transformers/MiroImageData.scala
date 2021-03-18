package uk.ac.wellcome.platform.transformer.miro.transformers

import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord
import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.image.ImageData

trait MiroImageData extends MiroLocation {

  def getImageData(miroRecord: MiroRecord,
                   version: Int): ImageData[IdState.Identifiable] =
    ImageData[IdState.Identifiable](
      id = IdState.Identifiable(
        sourceIdentifier = SourceIdentifier(
          identifierType = IdentifierType.MiroImageNumber,
          ontologyType = "Image",
          value = miroRecord.imageNumber
        )
      ),
      version = version,
      locations = List(getLocation(miroRecord))
    )
}
