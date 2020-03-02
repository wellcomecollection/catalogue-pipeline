package uk.ac.wellcome.display.models

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema

import uk.ac.wellcome.models.work.internal._

@Schema(
  name = "Note",
  description = "A note associated with the work."
)
case class DisplayNote(
  @Schema(description = "The note contents.") contents: List[String],
  @Schema(description = "The type of note.") noteType: DisplayNoteType,
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "Note"
)

@Schema(
  name = "NoteType",
  description = "Indicates the type of note associated with the work."
)
case class DisplayNoteType(
  @Schema(
    `type` = "String"
  ) id: String,
  @Schema(
    `type` = "String"
  ) label: String,
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "NoteType"
)

object DisplayNote {

  def apply(note: Note): DisplayNote =
    DisplayNote(List(note.content), DisplayNoteType(note))

  def merge(notes: List[DisplayNote]): List[DisplayNote] =
    notes
      .groupBy(_.noteType)
      .toList
      .map {
        case (noteType, notes) =>
          DisplayNote(notes.flatMap(_.contents), noteType)
      }
}

object DisplayNoteType {

  def apply(note: Note): DisplayNoteType =
    note match {
      case GeneralNote(_) =>
        DisplayNoteType("general-note", "Notes")
      case BibliographicalInformation(_) =>
        DisplayNoteType("bibliographic-info", "Bibliographic information")
      case FundingInformation(_) =>
        DisplayNoteType("funding-info", "Funding information")
      case TimeAndPlaceNote(_) =>
        DisplayNoteType("time-and-place-note", "Time and place note")
      case CreditsNote(_) =>
        DisplayNoteType("credits", "Creator/production credits")
      case ContentsNote(_) =>
        DisplayNoteType("contents", "Contents")
      case CiteAsNote(_) =>
        DisplayNoteType("reference", "Reference")
      case DissertationNote(_) =>
        DisplayNoteType("dissertation-note", "Dissertation note")
      case LocationOfOriginalNote(_) =>
        DisplayNoteType("location-of-original", "Location of original")
      case BindingInformation(_) =>
        DisplayNoteType("binding-detail", "Binding detail")
      case BiographicalNote(_) =>
        DisplayNoteType("biographical-note", "Biographical note")
      case ReproductionNote(_) =>
        DisplayNoteType("reproduction-note", "Reproduction note")
      case TermsOfUse(_) =>
        DisplayNoteType("terms-of-use", "Terms of use")
      case CopyrightNote(_) =>
        DisplayNoteType("copyright-note", "Copyright note")
      case PublicationsNote(_) =>
        DisplayNoteType("publication-note", "Publications note")
      case ExhibitionsNote(_) =>
        DisplayNoteType("exhibitions-note", "Exhibitions note")
      case AwardsNote(_) =>
        DisplayNoteType("awards-note", "Awards note")
      case OwnershipNote(_) =>
        DisplayNoteType("ownership-note", "Ownserhip note")
      case AcquisitionNote(_) =>
        DisplayNoteType("acquisition-note", "Acquisition note")
      case AppraisalNote(_) =>
        DisplayNoteType("appraisal-note", "Appraisal note")
      case AccrualsNote(_) =>
        DisplayNoteType("accruals-note", "Accruals note")
      case RelatedMaterial(_) =>
        DisplayNoteType("related-material", "Related material")
      case FindingAids(_) =>
        DisplayNoteType("finding-aids", "Finding aids")
      case ArrangementNote(_) =>
        DisplayNoteType("arrangement-note", "Arrangement")
    }
}
