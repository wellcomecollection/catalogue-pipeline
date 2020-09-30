package uk.ac.wellcome.platform.merger.generators

import uk.ac.wellcome.models.work.generators.ImageGenerators

trait WorksWithImagesGenerators extends ImageGenerators {
  def createMiroWork = createMiroWorkWith(List(createUnmergedMiroImage))

  def createInvisibleMetsSourceWork =
    createInvisibleMetsSourceWorkWith(images = List(createUnmergedMetsImage))
}
