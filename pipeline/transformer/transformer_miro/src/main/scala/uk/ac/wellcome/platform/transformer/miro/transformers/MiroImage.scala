package uk.ac.wellcome.platform.transformer.miro.transformers

import uk.ac.wellcome.models.work.internal.{
  Identifiable,
  IdentifierType,
  SourceIdentifier,
  UnmergedImage,
  Unminted
}
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord

trait MiroImage extends MiroLocations {

  def getImage(miroRecord: MiroRecord,
               version: Int): UnmergedImage[Identifiable, Unminted] =
    UnmergedImage(
      sourceIdentifier = SourceIdentifier(
        identifierType = IdentifierType("miro-image-number"),
        ontologyType = "Image",
        value = miroRecord.imageNumber
      ),
      version = version,
      location = getLocations(miroRecord).head
    )
}
