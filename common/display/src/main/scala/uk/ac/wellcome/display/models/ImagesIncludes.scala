package uk.ac.wellcome.display.models

sealed trait ImageInclude

object ImageInclude {
  case object VisuallySimilar extends ImageInclude
  case object WithSimilarFeatures extends ImageInclude
  case object WithSimilarColors extends ImageInclude
  case object SourceContributor extends ImageInclude
  case object SourceLanguages extends ImageInclude
}

sealed trait ImageIncludes {
  val visuallySimilar: Boolean
  val withSimilarFeatures: Boolean
  val withSimilarColors: Boolean
  val sourceContributor: Boolean
  val sourceLanguages: Boolean
}

case class SingleImageIncludes(
  visuallySimilar: Boolean,
  withSimilarFeatures: Boolean,
  withSimilarColors: Boolean,
  sourceContributor: Boolean,
  sourceLanguages: Boolean
) extends ImageIncludes

object SingleImageIncludes {
  import ImageInclude._

  def apply(includes: ImageInclude*): SingleImageIncludes =
    SingleImageIncludes(
      visuallySimilar = includes.contains(VisuallySimilar),
      withSimilarFeatures = includes.contains(WithSimilarFeatures),
      withSimilarColors = includes.contains(WithSimilarColors),
      sourceContributor = includes.contains(SourceContributor),
      sourceLanguages = includes.contains(SourceLanguages),
    )

  def none: SingleImageIncludes = SingleImageIncludes()
}

case class MultipleImagesIncludes(
  sourceContributor: Boolean,
  sourceLanguages: Boolean
) extends ImageIncludes {
  val visuallySimilar: Boolean = false
  val withSimilarFeatures: Boolean = false
  val withSimilarColors: Boolean = false
}

object MultipleImagesIncludes {
  import ImageInclude._

  def apply(includes: ImageInclude*): MultipleImagesIncludes =
    MultipleImagesIncludes(
      sourceContributor = includes.contains(SourceContributor),
      sourceLanguages = includes.contains(SourceLanguages),
    )

  def none: MultipleImagesIncludes = MultipleImagesIncludes()
}
