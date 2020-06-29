package uk.ac.wellcome.display.models

sealed trait ImageInclude

object ImageInclude {
  case object VisuallySimilar extends ImageInclude
}

case class SingleImageIncludes(includes: List[ImageInclude]) {
  def visuallySimilar = includes.contains(ImageInclude.VisuallySimilar)
}

object SingleImageIncludes {
  import ImageInclude._

  def apply(visuallySimilar: Boolean = false): SingleImageIncludes =
    SingleImageIncludes(
      List(
        if (visuallySimilar) Some(VisuallySimilar) else None
      ).flatten
    )
}
