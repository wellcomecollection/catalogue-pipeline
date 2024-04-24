package weco.pipeline.transformer.sierra.transformers.subjects

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{AbstractConcept, Concept, Subject}
import weco.pipeline.transformer.marc_common.models.MarcField
import weco.pipeline.transformer.marc_common.transformers.subjects.MarcConceptSubject
import weco.pipeline.transformer.text.TextNormalisation.TextNormalisationOps

// Populate wwork:subject
//
// Use MARC field "652". This is not documented but is a custom field used to
// represent brand names
object SierraBrandNameSubjects extends SierraSubjectsTransformer2 {
  override protected val subjectVarFields: Seq[String] =
    Seq("652")

  override protected def getSubject(
    field: MarcField
  ): Option[Subject[IdState.Unminted]] = SierraBrandNameSubject(field)

  private object SierraBrandNameSubject extends MarcConceptSubject {
    override protected val defaultSecondIndicator: String = "0"
    override protected def getPrimaryTypeConcepts(
      field: MarcField,
      idstate: Option[IdState.Identifiable]
    ): Seq[AbstractConcept[IdState.Unminted]] =
      field.subfields.filter(_.tag == "a").map {
        subfield =>
          val label = subfield.content.trimTrailingPeriod
          Concept(label = label).normalised.identifiable(idstate)
      }

    override protected def getFieldOntologyType(field: MarcField): String =
      "Concept"
  }
}
