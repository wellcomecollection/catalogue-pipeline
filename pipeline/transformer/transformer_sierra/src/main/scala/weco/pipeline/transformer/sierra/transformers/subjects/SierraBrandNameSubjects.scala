package weco.pipeline.transformer.sierra.transformers.subjects

import weco.catalogue.internal_model.work.{Concept, Subject}
import weco.catalogue.source_model.sierra.identifiers.SierraBibNumber
import weco.pipeline.transformer.sierra.transformers.SierraAgents
import weco.sierra.models.marc.VarField

// Populate wwork:subject
//
// Use MARC field "652". This is not documented but is a custom field used to
// represent brand names
object SierraBrandNameSubjects
    extends SierraSubjectsTransformer
    with SierraAgents {

  val subjectVarFields = List("652")

  def getSubjectsFromVarFields(bibId: SierraBibNumber,
                               varFields: List[VarField]) =
    varFields
      .subfieldsWithTag("a")
      .contents
      .map(label => Subject(label, List(Concept(label))))
}
