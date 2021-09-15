package weco.catalogue.internal_model.work

case class NoteType(id: String, label: String)

case class Note(contents: String, noteType: NoteType)

case object Note {
  object General {
    def apply(contents: String): Note =
      Note(contents = contents, noteType = NoteType(id = "general-note", label = "Notes"))
  }

  object BibliographicalInformation {
    def apply(contents: String): Note =
      Note(contents = contents, noteType = NoteType(id = "bibliographic-info", label = "Bibliographic information"))
  }

  object TimeAndPlace {
    def apply(contents: String): Note =
      Note(contents = contents, noteType = NoteType(id = "time-and-place-note", label = "Time and place note"))
  }

  object CreditsNote {
    def apply(contents: String): Note =
      Note(contents = contents, noteType = NoteType(id = "credits", label = "Creator/production credits"))
  }

  object ContentsNote {
    def apply(contents: String): Note =
      Note(contents = contents, noteType = NoteType(id = "contents", label = "Contents"))
  }

  object CiteAsNote {
    def apply(contents: String): Note =
      Note(contents = contents, noteType = NoteType(id = "reference", label = "Reference"))
  }

  object DissertationNote {
    def apply(contents: String): Note =
      Note(contents = contents, noteType = NoteType(id = "dissertation-note", label = "Dissertation note"))
  }

  object LocationOfOriginalNote {
    def apply(contents: String): Note =
      Note(contents = contents, noteType = NoteType(id = "location-of-original", label = "Location of original"))
  }

  object LocationOfDuplicatesNote {
    def apply(contents: String): Note =
      Note(contents = contents, noteType = NoteType(id = "location-of-duplicates", label = "Location of duplicates"))
  }

  object BindingInformation {
    def apply(contents: String): Note =
      Note(contents = contents, noteType = NoteType(id = "binding-detail", label = "Binding detail"))
  }

  object BiographicalNote {
    def apply(contents: String): Note =
      Note(contents = contents, noteType = NoteType(id = "biographical-note", label = "Biographical note"))
  }

  object ReproductionNote {
    def apply(contents: String): Note =
      Note(contents = contents, noteType = NoteType(id = "reproduction-note", label = "Reproduction note"))
  }

  object TermsOfUse {
    def apply(contents: String): Note =
      Note(contents = contents, noteType = NoteType(id = "terms-of-use", label = "Terms of use"))

    def unapply(note: Note): Option[String] =
      note.noteType.id match {
        case "terms-of-use" => Some(note.contents)
        case _              => None
      }
  }

  object CopyrightNote {
    def apply(contents: String): Note =
      Note(contents = contents, noteType = NoteType(id = "copyright-note", label = "Copyright note"))
  }

  object PublicationsNote {
    def apply(contents: String): Note =
      Note(contents = contents, noteType = NoteType(id = "publication-note", label = "Publications note"))
  }

  object ExhibitionsNote {
    def apply(contents: String): Note =
      Note(contents = contents, noteType = NoteType(id = "exhibitions-note", label = "Exhibitions note"))
  }

  object AwardsNote {
    def apply(contents: String): Note =
      Note(contents = contents, noteType = NoteType(id = "awards-note", label = "Awards note"))
  }

  object OwnershipNote {
    def apply(contents: String): Note =
      Note(contents = contents, noteType = NoteType(id = "ownership-note", label = "Ownership note"))
  }

  object AcquisitionNote {
    def apply(contents: String): Note =
      Note(contents = contents, noteType = NoteType(id = "acquisition-note", label = "Acquisition note"))
  }

  object AppraisalNote {
    def apply(contents: String): Note =
      Note(contents = contents, noteType = NoteType(id = "appraisal-note", label = "Appraisal note"))
  }

  object AccrualsNote {
    def apply(contents: String): Note =
      Note(contents = contents, noteType = NoteType(id = "accruals-note", label = "Accruals note"))
  }

  object RelatedMaterial {
    def apply(contents: String): Note =
      Note(contents = contents, noteType = NoteType(id = "related-material", label = "Related material"))
  }

  object FindingAids {
    def apply(contents: String): Note =
      Note(contents = contents, noteType = NoteType(id = "finding-aids", label = "Finding aids"))
  }

  object ArrangementNote {
    def apply(contents: String): Note =
      Note(contents = contents, noteType = NoteType(id = "arrangement-note", label = "Arrangement"))
  }

  object LetteringNote {
    def apply(contents: String): Note =
      Note(contents = contents, noteType = NoteType(id = "lettering-note", label = "Lettering note"))
  }

  object LanguageNote {
    def apply(contents: String): Note =
      Note(contents = contents, noteType = NoteType(id = "language-note", label = "Language note"))
  }

  object ReferencesNote {
    def apply(contents: String): Note =
      Note(contents = contents, noteType = NoteType(id = "references-note", label = "References note"))
  }
}
