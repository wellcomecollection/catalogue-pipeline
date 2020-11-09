package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.sierra.source.{
  SierraBibData,
  SierraQueryOps,
  VarField
}

case class NotesField(createNote: String => Note,
                      suppressedSubfields: Set[String] = Set.empty)

object SierraNotes extends SierraDataTransformer with SierraQueryOps {

  type Output = List[Note]

  val notesFields = Map(
    "500" -> NotesField(GeneralNote),
    "501" -> NotesField(GeneralNote),
    "502" -> NotesField(DissertationNote),
    "504" -> NotesField(BibliographicalInformation),
    "505" -> NotesField(ContentsNote),
    "508" -> NotesField(CreditsNote),
    "510" -> NotesField(PublicationsNote),
    "511" -> NotesField(CreditsNote),
    "518" -> NotesField(TimeAndPlaceNote),
    "524" -> NotesField(CiteAsNote),
    "533" -> NotesField(ReproductionNote),
    "534" -> NotesField(ReproductionNote),
    "535" -> NotesField(LocationOfOriginalNote),
    "536" -> NotesField(FundingInformation),
    "540" -> NotesField(TermsOfUse),
    "542" -> NotesField(CopyrightNote),
    "545" -> NotesField(BiographicalNote),
    "547" -> NotesField(GeneralNote),
    "562" -> NotesField(GeneralNote),
    "563" -> NotesField(BindingInformation, suppressedSubfields = Set("5")),
    "581" -> NotesField(PublicationsNote),
    "585" -> NotesField(ExhibitionsNote),
    "586" -> NotesField(AwardsNote),
    "593" -> NotesField(CopyrightNote),
  )

  def apply(bibData: SierraBibData) =
    bibData.varFields
      .map {
        case vf @ VarField(_, Some(marcTag), _, _, _, _) =>
            Some((vf, notesFields.get(marcTag)))
        case _ => None
      }
      .collect {
        case Some((vf, Some(notesField))) => (vf, notesField)
      }
      .map { case (vf, NotesField(createNote, suppressedSubfields)) =>
        val contents =
          vf
            .subfieldsWithoutTags(suppressedSubfields.toSeq: _*)
            .contents
            .mkString(" ")

        createNote(contents)
      }
}
