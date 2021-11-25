package weco.catalogue.internal_model.work

case class NoteType(id: String, label: String)

case class Note(noteType: NoteType, contents: String)

// We used to have a sealed trait for Note, with a separate subclass for each
// type of Note.  We moved away from that to a parametrised NoteType for
// a few reasons:
//
//    - Having lots of subtypes means Circe has to create lots of implicit JSON
//      encoders and decoders, which slows the build down
//    - Every time we change the note types, we have to update the API code before
//      we can use them
//    - We don't actually care about NoteType being an enumerated type
//
case object NoteType {
  val GeneralNote = NoteType(id = "general-note", label = "Notes")
  val BibliographicalInformation =
    NoteType(id = "bibliographic-info", label = "Bibliographic information")
  val FundingInformation =
    NoteType(id = "funding-info", label = "Funding information")
  val TimeAndPlaceNote =
    NoteType(id = "time-and-place-note", label = "Time and place note")
  val CreditsNote =
    NoteType(id = "credits", label = "Creator/production credits")
  val ContentsNote = NoteType(id = "contents", label = "Contents")
  val CiteAsNote = NoteType(id = "reference", label = "Reference")
  val DissertationNote =
    NoteType(id = "dissertation-note", label = "Dissertation note")
  val LocationOfOriginalNote =
    NoteType(id = "location-of-original", label = "Location of original")
  val LocationOfDuplicatesNote =
    NoteType(id = "location-of-duplicates", label = "Location of duplicates")
  val BindingInformation =
    NoteType(id = "binding-detail", label = "Binding detail")
  val BiographicalNote =
    NoteType(id = "biographical-note", label = "Biographical note")
  val ReproductionNote =
    NoteType(id = "reproduction-note", label = "Reproduction note")
  val TermsOfUse = NoteType(id = "terms-of-use", label = "Terms of use")
  val CopyrightNote = NoteType(id = "copyright-note", label = "Copyright note")
  val PublicationsNote =
    NoteType(id = "publication-note", label = "Publications note")
  val ExhibitionsNote =
    NoteType(id = "exhibitions-note", label = "Exhibitions note")
  val AwardsNote = NoteType(id = "awards-note", label = "Awards note")
  val OwnershipNote = NoteType(id = "ownership-note", label = "Ownership note")
  val AcquisitionNote =
    NoteType(id = "acquisition-note", label = "Acquisition note")
  val AppraisalNote = NoteType(id = "appraisal-note", label = "Appraisal note")
  val AccrualsNote = NoteType(id = "accruals-note", label = "Accruals note")
  val RelatedMaterial =
    NoteType(id = "related-material", label = "Related material")
  val FindingAids = NoteType(id = "finding-aids", label = "Finding aids")
  val ArrangementNote = NoteType(id = "arrangement-note", label = "Arrangement")
  val LetteringNote = NoteType(id = "lettering-note", label = "Lettering note")
  val LanguageNote = NoteType(id = "language-note", label = "Language note")
  val ReferencesNote =
    NoteType(id = "references-note", label = "References note")
  val ColophonNote =
    NoteType(id = "colophon", label = "Colophon note")

  val BeginsNote: NoteType = NoteType(id = "begins-note", label = "Begins")
  val EndsNote: NoteType = NoteType(id = "ends-note", label = "Ends")
  val LocusNote: NoteType = NoteType(id = "locus-note", label = "Locus")
  val HandNote: NoteType = NoteType(id = "hand-note", label = "Hand note")
}

case object Note {
  object TermsOfUse {
    def unapply(n: Note): Option[String] =
      n.noteType match {
        case NoteType.TermsOfUse => Some(n.contents)
        case _                   => None
      }
  }
}
