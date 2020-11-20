package uk.ac.wellcome.display.models

sealed trait ImageInclude

object ImageInclude {
  case object VisuallySimilar extends ImageInclude
  case object WithSimilarFeatures extends ImageInclude
  case object WithSimilarColors extends ImageInclude
}

case class SingleImageIncludes(
  visuallySimilar: Boolean,
  withSimilarFeatures: Boolean,
  withSimilarColors: Boolean
) {
  import ImageInclude._

  def includes: List[ImageInclude] =
    List(
      if (visuallySimilar) Some(VisuallySimilar) else None,
      if (withSimilarFeatures) Some(WithSimilarFeatures) else None,
      if (withSimilarColors) Some(WithSimilarColors) else None,
    ).flatten
}

object SingleImageIncludes {
  import ImageInclude._

  def apply(includes: List[ImageInclude]): SingleImageIncludes =
    SingleImageIncludes(
      visuallySimilar = includes.contains(VisuallySimilar),
      withSimilarFeatures = includes.contains(WithSimilarFeatures),
      withSimilarColors = includes.contains(WithSimilarColors)
    )
}
