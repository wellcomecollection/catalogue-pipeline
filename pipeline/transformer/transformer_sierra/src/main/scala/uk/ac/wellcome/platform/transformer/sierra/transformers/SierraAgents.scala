package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.sierra.source.{
  MarcSubfield,
  SierraQueryOps
}

trait SierraAgents extends SierraQueryOps {
  // This is used to construct a Person from MARc tags 100, 700 and 600.
  // For all these cases:
  //  - subfield $a populates the person label
  //  - subfield $b populates the person numeration
  //  - subfield $c populates the person prefixes
  //
  def getPerson(
    subfields: List[MarcSubfield],
    normalisePerson: Boolean = false): Option[Person[IdState.Unminted]] =
    getLabel(subfields).map { label =>
      // The rule is to only normalise the 'Person' label when a contributor.  Strictly a 'Person' within
      // 'Subjects' (sourced from Marc 600) should not be normalised -- however, as these labels
      // are not expected to have punctuation normalisation should not change the 'Person' label for 'Subjects'
      // In which case normalisation is effectively a no-op and the test can be removed and Person.normalised
      // always returned when confident in the data.
      if (normalisePerson)
        Person.normalised(
          label = label,
          prefix = None,
          numeration = None
        )
      else
        Person(
          id = IdState.Unidentifiable,
          label = label,
          prefix = None,
          numeration = None
        )
    }

  // This is used to construct an Organisation from MARC tags 110 and 710.
  // For all entries:
  //  - Subfield $a is "label"
  //  - Subfield $0 is used to populate "identifiers". The identifier scheme is lc-names.
  //
  def getOrganisation(
    subfields: List[MarcSubfield]): Option[Organisation[IdState.Unminted]] =
    getLabel(subfields).map { label =>
      Organisation.normalised(label = label)
    }

  def getMeeting(
    subfields: List[MarcSubfield]): Option[Meeting[IdState.Unminted]] =
    getLabel(subfields.withTags("a", "c", "d", "j", "t", "0"))
      .map { label =>
        Meeting.normalised(label = label)
      }

  /* Given an agent and the associated MARC subfields, look for instances of subfield $0,
   * which are used for identifiers.
   *
   * This methods them (if present) and wraps the agent in Unidentifiable or Identifiable
   * as appropriate.
   */
  def identify(subfields: List[MarcSubfield],
               ontologyType: String): IdState.Unminted = {

    // We take the contents of subfield $0.  They may contain inconsistent
    // spacing and punctuation, such as:
    //
    //    " nr 82270463"
    //    "nr 82270463"
    //    "nr 82270463.,"
    //
    // which all refer to the same identifier.
    //
    // For consistency, we remove all whitespace and some punctuation
    // before continuing.
    val codes = subfields.collect {
      case MarcSubfield("0", content) => content.replaceAll("[.,\\s]", "")
    }

    // If we get exactly one value, we can use it to identify the record.
    // Some records have multiple instances of subfield $0 (it's a repeatable
    // field in the MARC spec).
    codes.distinct match {
      case Seq(code) =>
        IdState.Identifiable(
          SourceIdentifier(
            identifierType = IdentifierType("lc-names"),
            value = code,
            ontologyType = ontologyType
          )
        )
      case _ => IdState.Unidentifiable
    }
  }

  def getLabel(subfields: List[MarcSubfield]): Option[String] =
    subfields.filter { s =>
      List("a", "b", "c", "d", "t").contains(s.tag)
    } map (_.content) match {
      case Nil          => None
      case nonEmptyList => Some(nonEmptyList mkString " ")
    }
}
