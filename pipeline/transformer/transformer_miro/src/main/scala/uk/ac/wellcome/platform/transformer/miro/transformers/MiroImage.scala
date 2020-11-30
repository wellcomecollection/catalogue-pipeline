package uk.ac.wellcome.platform.transformer.miro.transformers

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord

trait MiroImage extends MiroLocation {

  def getImage(miroRecord: MiroRecord, version: Int): Image[ImageState.Source] =
    Image[ImageState.Source](
      state = ImageState.Source(
        sourceIdentifier = SourceIdentifier(
          identifierType = IdentifierType("miro-image-number"),
          ontologyType = "Image",
          value = miroRecord.imageNumber
        )
      ),
      version = version,
      locations = List(getLocation(miroRecord))
    )
}
