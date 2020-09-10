package uk.ac.wellcome.platform.transformer.miro.transformers

import uk.ac.wellcome.models.work.internal.{
  Id,
  IdentifierType,
  SourceIdentifier,
  UnmergedImage,
}
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord

trait MiroImage extends MiroLocation {

  def getImage(miroRecord: MiroRecord,
               version: Int): UnmergedImage[Id.Identifiable, Id.Unminted] =
    UnmergedImage(
      sourceIdentifier = SourceIdentifier(
        identifierType = IdentifierType("miro-image-number"),
        ontologyType = "Image",
        value = miroRecord.imageNumber
      ),
      version = version,
      location = getLocation(miroRecord)
    )
}
