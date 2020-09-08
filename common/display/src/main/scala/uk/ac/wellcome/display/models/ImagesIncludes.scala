package uk.ac.wellcome.display.models

sealed trait ImageInclude

object ImageInclude {
  case object VisuallySimilar extends ImageInclude
  case object WithSimilarFeatures extends ImageInclude
  case object WithSimilarColors extends ImageInclude
}

case class SingleImageIncludes(includes: List[ImageInclude]) {
  def visuallySimilar = includes.contains(ImageInclude.VisuallySimilar)
  def withSimilarFeatures = includes.contains(ImageInclude.WithSimilarFeatures)
  def withSimilarColors = includes.contains(ImageInclude.WithSimilarColors)
}

object SingleImageIncludes {
  import ImageInclude._

  def apply(visuallySimilar: Boolean = false,
            withSimilarFeatures: Boolean = false,
            withSimilarColors: Boolean = false): SingleImageIncludes =
    SingleImageIncludes(
      List(
        if (visuallySimilar) Some(VisuallySimilar) else None,
        if (withSimilarFeatures) Some(WithSimilarFeatures) else None,
        if (withSimilarColors) Some(WithSimilarColors) else None,
      ).flatten
    )
}
