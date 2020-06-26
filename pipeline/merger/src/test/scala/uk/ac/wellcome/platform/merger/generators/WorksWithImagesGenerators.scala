package uk.ac.wellcome.platform.merger.generators

import uk.ac.wellcome.models.work.generators.ImageGenerators

trait WorksWithImagesGenerators extends ImageGenerators {
  def createMiroWork = createMiroWorkWith(List(createUnmergedMiroImage))

  def createUnidentifiedInvisibleMetsWork = createUnidentifiedInvisibleMetsWorkWith(images = List(createUnmergedMetsImage))
}
