package weco.pipeline.transformer.sierra.transformers.subjects

import weco.catalogue.internal_model.work.{Concept, Subject}
import weco.pipeline.transformer.sierra.transformers.SierraConcepts
import weco.pipeline.transformer.transformers.ConceptsTransformer
//import weco.pipeline.transformer.sierra.transformers.SierraAgents
import weco.sierra.models.identifiers.SierraBibNumber
import weco.sierra.models.marc.VarField

// Populate wwork:subject
//
// Use MARC field "652". This is not documented but is a custom field used to
// represent brand names
object SierraBrandNameSubjects
    extends SierraSubjectsTransformer
    with ConceptsTransformer
    with SierraConcepts {

  val subjectVarFields = List("652")

  def getSubjectsFromVarFields(bibId: SierraBibNumber,
                               varFields: List[VarField]): Output =
    varFields
      .subfieldsWithTag("a")
      .contents
      .map(
        label =>
          new Subject(
            id = addIdentifierFromText(ontologyType = "Subject", label = label),
            label = label,
            concepts = List(Concept(label).identifiable())))
}
