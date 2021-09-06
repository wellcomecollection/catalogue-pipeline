package weco.pipeline.transformer.sierra.transformers

import weco.catalogue.internal_model.work._
import weco.sierra.models.SierraQueryOps
import weco.sierra.models.data.SierraBibData
import weco.sierra.models.marc.VarField

object SierraNotes extends SierraDataTransformer with SierraQueryOps {

  type Output = List[Note]
  private val globallySuppressedSubfields = Set("5")

  val notesFields = Map(
    "500" -> createNoteFromContents(GeneralNote),
    "501" -> createNoteFromContents(GeneralNote),
    "502" -> createNoteFromContents(DissertationNote),
    "504" -> createNoteFromContents(BibliographicalInformation),
    "505" -> createNoteFromContents(ContentsNote),
    "506" -> createNoteFromContents(TermsOfUse),
    "508" -> createNoteFromContents(CreditsNote),
    "510" -> createNoteFromContents(ReferencesNote),
    "511" -> createNoteFromContents(CreditsNote),
    "514" -> createNoteFromContents(LetteringNote),
    "518" -> createNoteFromContents(TimeAndPlaceNote),
    "524" -> createNoteFromContents(CiteAsNote),
    "533" -> createNoteFromContents(ReproductionNote),
    "534" -> createNoteFromContents(ReproductionNote),
    "535" -> createLocationOfNote _,
    "536" -> createNoteFromContents(FundingInformation),
    "540" -> createNoteFromContents(TermsOfUse),
    "542" -> createNoteFromContents(CopyrightNote),
    "545" -> createNoteFromContents(BiographicalNote),
    "546" -> createNoteFromContents(LanguageNote),
    "547" -> createNoteFromContents(GeneralNote),
    "562" -> createNoteFromContents(GeneralNote),
    "563" -> createNoteFromContents(BindingInformation),
    "581" -> createNoteFromContents(PublicationsNote),
    "585" -> createNoteFromContents(ExhibitionsNote),
    "586" -> createNoteFromContents(AwardsNote),

    // 591 subfield ǂ9 contains barcodes that we don't want to show on /works.
    "591" -> createNoteFromContents(GeneralNote, suppressedSubfields = Set("9")),

    "593" -> createNoteFromContents(CopyrightNote),
  )

  def apply(bibData: SierraBibData): List[Note] =
    bibData.varFields
      .map {
        // For the 561 "Ownership and Custodial History" field, we only want
        // to expose the note when the 1st indicator is 1 ("public note").
        // We don't want to expose the field otherwise.
        //
        // See https://www.loc.gov/marc/bibliographic/bd561.html
        case vf @ VarField(_, Some("561"), _, Some("1"), _, _) =>
          Some((vf, Some(createNoteFromContents(OwnershipNote))))

        case vf @ VarField(_, Some(marcTag), _, _, _, _) =>
          Some((vf, notesFields.get(marcTag)))
        case _ => None
      }
      .collect {
        case Some((vf, Some(createNote))) => createNote(vf)
      }

  private def createNoteFromContents(
    createNote: String => Note,
    suppressedSubfields: Set[String] = Set()): VarField => Note =
    (varField: VarField) => {
      val contents =
        varField
          .subfieldsWithoutTags((globallySuppressedSubfields ++ suppressedSubfields).toSeq: _*)
          .contents
          .mkString(" ")

      createNote(contents)
    }

  // In MARC 535, indicator 1 takes the following values:
  //
  //    1 = holder of originals
  //    2 = holder of duplicates
  //
  private def createLocationOfNote(vf: VarField): Note =
    vf.indicator1 match {
      case Some("2") => createNoteFromContents(LocationOfDuplicatesNote)(vf)
      case _         => createNoteFromContents(LocationOfOriginalNote)(vf)
    }
}
