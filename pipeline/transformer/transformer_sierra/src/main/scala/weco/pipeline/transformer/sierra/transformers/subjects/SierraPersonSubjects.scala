package weco.pipeline.transformer.sierra.transformers.subjects

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work._

import scala.util.{Failure, Success, Try}
import weco.pipeline.transformer.marc_common.models.MarcField
import weco.pipeline.transformer.marc_common.transformers.MarcPerson

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
    with ExcludeMeshIds {

  override protected val subjectVarFields: List[String] = List("600")
  private object PersonAsSubjectConcept extends MarcPerson with ExcludeMeshIds {
    override protected val labelSubfieldTags: Seq[String] =
      Seq("a", "b", "c", "d", "t", "p", "n", "q", "l")
  }
//TODO: I think this needs a bit of commentary.
  // Some subfields form part of the concept involved, and some do something else.
  override def getSubjectConcepts(
    field: MarcField
  ): Try[Seq[AbstractRootConcept[IdState.Unminted]]] =
    PersonAsSubjectConcept(field) match {
      case Success(person) =>
        Success(Seq(person) ++ getGeneralSubdivisions(field).map(Concept(_)))
      case Failure(exception) => Failure(exception)
    }

  private def getRoles(field: MarcField): Seq[String] =
    field.subfields.filter(_.tag == "e").map(_.content)
  private def getGeneralSubdivisions(field: MarcField): Seq[String] =
    field.subfields.filter(_.tag == "x").map(_.content)

  override protected def getLabel(field: MarcField): Option[String] = {
    Option(
      (field.subfields
        .filter(subfield => labelSubfields.contains(subfield.tag))
        .map(_.content) ++ getRoles(field) ++ getGeneralSubdivisions(field))
        .mkString(" ")
    ).filter(_.nonEmpty)
  }

  override protected val labelSubfields: Seq[String] =
    Seq("a", "b", "c", "d", "t", "p", "n", "q", "l")
  override protected val ontologyType: String = "Person"
}
