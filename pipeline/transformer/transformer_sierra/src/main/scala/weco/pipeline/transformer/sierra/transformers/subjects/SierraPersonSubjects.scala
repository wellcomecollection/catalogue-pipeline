package weco.pipeline.transformer.sierra.transformers.subjects

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work._
import weco.pipeline.transformer.sierra.transformers.SierraAgents
import weco.sierra.models.identifiers.SierraBibNumber
import weco.sierra.models.marc.{Subfield, VarField}

// Populate wwork:subject
//
// Use MARC field "600" where the second indicator is not 7.
//
// The concepts come from:
//
//    - The person
//    - The contents of subfields $t and $x (title and general subdivision),
//      both as Concepts
//
// The label is constructed concatenating subfields $a, $b, $c, $d, $e,
// where $d and $e represent the person's dates and roles respectively.
//
// The person can be identified if there is an identifier in subfield $0 and the second indicator is "0".
// If second indicator is anything other than 0, we don't expose the identifier for now.
//
object SierraPersonSubjects
    extends SierraSubjectsTransformer
    with SierraAgents {

  val subjectVarFields = List("600")

  def getSubjectsFromVarFields(bibId: SierraBibNumber,
                               varFields: List[VarField]): Output = {
    // Second indicator 7 means that the subject authority is something other
    // than library of congress or mesh. Some MARC records have duplicated subjects
    // when the same subject has more than one authority (for example mesh and FAST),
    // which causes duplicated subjects to appear in the API.
    //
    // So let's filter anything that is from another authority for now.
    varFields
      .filterNot { _.indicator2.contains("7") }
      .flatMap { varField: VarField =>
        val subfields = varField.subfields
        val maybePerson =
          getPerson(subfields)
        val generalSubdivisions =
          varField.subfields
            .collect {
              case Subfield("x", content) => content
            }

        maybePerson.map { person =>
          val label = getPersonSubjectLabel(
            person = person,
            roles = getRoles(subfields),
            dates = getDates(subfields),
            generalSubdivisions = generalSubdivisions
          )

          Subject(
            label = label,
            concepts = getConcepts(person, generalSubdivisions),
            id = identify(varField, "Subject")
          )
        }
      }
  }

  private def getPersonSubjectLabel(person: Person[IdState.Unminted],
                                    roles: List[String],
                                    dates: Option[String],
                                    generalSubdivisions: List[String]): String =
    (List(person.label) ++ roles ++ generalSubdivisions)
      .mkString(" ")

  private def getConcepts(person: Person[IdState.Unminted],
                          generalSubdivisions: List[String])
    : List[AbstractRootConcept[IdState.Unminted]] =
    person +: generalSubdivisions.map(Concept(_))

  private def getRoles(secondarySubfields: List[Subfield]) =
    secondarySubfields.collect { case Subfield("e", role) => role }
  private def getDates(secondarySubfields: List[Subfield]) =
    secondarySubfields.find(_.tag == "d").map(_.content)
}
