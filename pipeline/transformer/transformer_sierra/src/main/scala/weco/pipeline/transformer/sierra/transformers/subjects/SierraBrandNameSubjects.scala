package weco.pipeline.transformer.sierra.transformers.subjects

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{AbstractRootConcept, Concept}
import weco.pipeline.transformer.marc_common.models.MarcField
import weco.pipeline.transformer.sierra.transformers.SierraConcepts
import weco.pipeline.transformer.transformers.ConceptsTransformer

import scala.util.{Failure, Success, Try}

// Populate wwork:subject
//
// Use MARC field "652". This is not documented but is a custom field used to
// represent brand names
object SierraBrandNameSubjects
    extends SierraSubjectsTransformer
    with ConceptsTransformer
    with SierraConcepts {

  override protected val labelSubfields: Seq[String] =
    Seq("a")
  override protected val subjectVarFields: List[String] = List("652")
  override protected val ontologyType: String = "Concept"

  override def getSubjectConcepts(
    field: MarcField
  ): Try[Seq[AbstractRootConcept[IdState.Unminted]]] = {
    getLabel(field) match {
      case Some(label) => Success(Seq(Concept(label).identifiable()))
      case None => Failure(new Exception("could not extract label from field"))
    }
  }
}
