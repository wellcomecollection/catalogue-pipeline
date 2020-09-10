package uk.ac.wellcome.models.work.internal

case class Genre[+DataId](
  label: String,
  concepts: List[AbstractConcept[DataId]] = Nil,
  ontologyType: String = "Genre"
)
