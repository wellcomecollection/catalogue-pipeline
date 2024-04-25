package weco.pipeline.transformer.marc_common.transformers.subjects

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{
  AbstractRootConcept,
  Concept,
  Subject
}
import weco.pipeline.transformer.marc_common.models.MarcField
import weco.pipeline.transformer.marc_common.transformers.MarcPerson

import scala.util.{Failure, Success, Try}

trait MarcPersonSubject extends MarcSubject with OnlyLocIds {
  override def apply(field: MarcField): Option[Subject[IdState.Unminted]] =
    getSubject(field)

  private object PersonAsSubjectConcept extends MarcPerson with OnlyLocIds {
    override protected val labelSubfieldTags: Seq[String] =
      Seq("a", "b", "c", "d", "t", "p", "n", "q", "l")
  }
  // TODO: I think this needs a bit of commentary.
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

object MarcPersonSubject extends MarcPersonSubject
