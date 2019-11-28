package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.transformable.sierra.SierraBibNumber
import uk.ac.wellcome.platform.transformer.sierra.source.{
  SierraBibData,
  SierraQueryOps
}

case class NotesField(createNote: String => Note,
                      supressedSubfields: Set[String] = Set.empty)

object SierraNotes extends SierraTransformer with SierraQueryOps {

  type Output = List[Note]

  val notesFields =
    List(
      "500" -> NotesField(GeneralNote(_)),
      "501" -> NotesField(GeneralNote(_)),
      "502" -> NotesField(DissertationNote(_)),
      "504" -> NotesField(BibliographicalInformation(_)),
      "505" -> NotesField(ContentsNote(_)),
      "508" -> NotesField(CreditsNote(_)),
      "511" -> NotesField(CreditsNote(_)),
      "518" -> NotesField(TimeAndPlaceNote(_)),
      "524" -> NotesField(CiteAsNote(_)),
      "535" -> NotesField(LocationOfOriginalNote(_)),
      "536" -> NotesField(FundingInformation(_)),
      "545" -> NotesField(BibliographicalInformation(_)),
      "547" -> NotesField(GeneralNote(_)),
      "562" -> NotesField(GeneralNote(_)),
      "563" -> NotesField(BindingInformation(_), Set("5")),
    )

  def apply(bibId: SierraBibNumber, bibData: SierraBibData) =
    notesFields
      .flatMap {
        case (tag, NotesField(createNote, supressedSubfields)) =>
          bibData
            .varfieldsWithTags(tag)
            .map { varfield =>
              varfield
                .subfieldsWithoutTags(supressedSubfields.toSeq:_*)
                .contents.mkString(" ")
            }
            .map(createNote)
      }
}
