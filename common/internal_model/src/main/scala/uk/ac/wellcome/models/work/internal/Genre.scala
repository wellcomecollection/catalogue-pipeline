package uk.ac.wellcome.models.work.internal

case class Genre[+State](
  label: String,
  concepts: List[AbstractConcept[State]] = Nil
)

object Genre {
  def normalised[State](
    label: String,
    concepts: List[AbstractConcept[State]]): Genre[State] = {
    val normalisedLabel =
      label
        .stripSuffix(".")
        .trim
        .replace("Electronic Books", "Electronic books")

    Genre(
      label = normalisedLabel,
      concepts = concepts
    )
  }
}
