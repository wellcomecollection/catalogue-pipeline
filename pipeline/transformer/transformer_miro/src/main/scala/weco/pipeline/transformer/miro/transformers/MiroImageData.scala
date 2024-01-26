package weco.pipeline.transformer.miro.transformers

import weco.catalogue.internal_model.identifiers.{IdState, IdentifierType, SourceIdentifier}
import weco.catalogue.internal_model.image.ImageData
import weco.catalogue.source_model.miro.MiroSourceOverrides
import weco.pipeline.transformer.miro.source.MiroRecord

trait MiroImageData extends MiroLocation {

  def getImageData(
    miroRecord: MiroRecord,
    overrides: MiroSourceOverrides,
    version: Int
  ): ImageData[IdState.Identifiable] =
    ImageData[IdState.Identifiable](
      id = IdState.Identifiable(
        sourceIdentifier = SourceIdentifier(
          identifierType = IdentifierType.MiroImageNumber,
          ontologyType = "Image",
          value = miroRecord.imageNumber
        )
      ),
      version = version,
      locations = List(getLocation(miroRecord, overrides))
    )
}
