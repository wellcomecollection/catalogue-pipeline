package weco.pipeline.transformer.sierra.transformers

import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.{Meeting, Organisation, Person}
import weco.pipeline.transformer.transformers.ConceptsTransformer
import weco.sierra.models.SierraQueryOps
import weco.sierra.models.marc.{Subfield, VarField}

trait SierraAgents
    extends SierraQueryOps
    with ConceptsTransformer
    with SierraAbstractConcepts {
  // This is used to construct a Person from MARc tags 100, 700 and 600.
  // For all these cases:
  //  - subfield $a populates the person label
  //  - subfield $b populates the person numeration
  //  - subfield $c populates the person prefixes
  //
  def getPerson(
    subfields: List[Subfield],
    normalisePerson: Boolean = false
  ): Option[Person[IdState.Unminted]] =
    getLabel(subfields).map { label =>
      val person =
        Person(
          id = IdState.Unidentifiable,
          label = label,
          prefix = None,
          numeration = None
        )

      // The rule is to only normalise the 'Person' label when a contributor.  Strictly a 'Person' within
      // 'Subjects' (sourced from Marc 600) should not be normalised -- however, as these labels
      // are not expected to have punctuation normalisation should not change the 'Person' label for 'Subjects'
      // In which case normalisation is effectively a no-op and the test can be removed and Person.normalised
      // always returned when confident in the data.
      if (normalisePerson)
        person.normalised
      else
        person
    }

  // This is used to construct an Organisation from MARC tags 110 and 710.
  // For all entries:
  //  - Subfield $a is "label"
  //  - Subfield $0 is used to populate "identifiers". The identifier scheme is lc-names.
  //  - Subfield $n is "Number of part/section/meeting", which we don't want to include
  //    in the label.
  //
  def getOrganisation(
    subfields: List[Subfield]
  ): Option[Organisation[IdState.Unminted]] =
    getLabel(subfields.filterNot(_.tag == "n"))
      .map { Organisation(_).normalised }

  def getMeeting(subfields: List[Subfield]): Option[Meeting[IdState.Unminted]] =
    getLabel(subfields.withTags("a", "c", "d", "t"))
      .map { Meeting(_).normalised }

  /* Given an agent and the associated MARC subfields, look for instances of subfield $0,
   * which are used for identifiers.
   *
   * This method extracts them (if present) and wraps the agent in Unidentifiable or Identifiable
   * as appropriate.
   *
   * Agents are only identified within the LCNames or LabelDerived schemata.
   *
   */
//

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
  protected def getIdentifierSubfieldContents(
    varField: VarField
  ): List[String] =
    varField.subfields.collect { case Subfield("0", content) =>
      content.replaceAll("[.,\\s]", "")
    }.distinct

  protected def maybeAddIdentifier(
    ontologyType: String,
    varField: VarField,
    identifierSubfieldContent: String
  ): IdState.Unminted =
    IdState.Identifiable(
      SourceIdentifier(
        identifierType = IdentifierType.LCNames,
        value = identifierSubfieldContent,
        ontologyType = ontologyType
      )
    )

  def getLabel(varField: VarField): Option[String] =
    getLabel(varField.subfields)

  def getLabel(subfields: List[Subfield]): Option[String] = {
    val contents =
      subfields
        .filter { s =>
          List("a", "b", "c", "d", "t", "p", "n", "q").contains(s.tag)
        }
        .filterNot { _.content.trim.isEmpty }
        .map { _.content }

    contents match {
      case Nil          => None
      case nonEmptyList => Some(nonEmptyList mkString " ")
    }
  }
}
