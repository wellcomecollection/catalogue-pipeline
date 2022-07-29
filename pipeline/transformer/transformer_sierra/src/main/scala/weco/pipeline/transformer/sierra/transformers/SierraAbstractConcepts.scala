package weco.pipeline.transformer.sierra.transformers
import grizzled.slf4j.Logging
import weco.pipeline.transformer.text.TextNormalisation._
import weco.catalogue.internal_model.identifiers.IdState
import weco.sierra.models.marc.VarField

trait SierraAbstractConcepts extends Logging {
  protected def getLabel(varField: VarField): Option[String]
  protected def getIdentifierSubfieldContents(varField: VarField): List[String]
  protected def maybeAddIdentifier(
    ontologyType: String,
    varField: VarField,
    identifierSubfieldContent: String): IdState.Unminted

  /**
    * Returns an IdState populated by looking at the identifier ($0) subfields in the given varField
    *
    * There are three-and-a-half possible scenarios:
    *
    *   - Exactly one identifier field: use that
    *     - unless it is to be ignored for type-specific reasons in maybeAddIdentifier
    *   - No identifier fields: create an identifier for it
    *   - Multiple identifier fields: unidentifiable, we don't know what to use
    *
    */
  def getIdState(ontologyType: String, varField: VarField): IdState.Unminted = {
    getIdentifierSubfieldContents(varField) match {
      case Seq(subfieldContent) =>
        maybeAddIdentifier(
          ontologyType = ontologyType,
          varField = varField,
          identifierSubfieldContent = subfieldContent
        )
      case Nil =>
        addIdentifierFromVarfieldText(ontologyType, varField)
      case _ =>
        warn(
          s"unable to identify has, multiple identifier subfields found on $varField")
        IdState.Unidentifiable
    }
  }

  private def addIdentifierFromVarfieldText(
    ontologyType: String,
    varField: VarField): IdState.Unminted =
    getLabel(varField) match {
      case Some(label) =>
        addIdentifierFromText(
          ontologyType = ontologyType,
          label = label.trimTrailingPeriod)
      case None => IdState.Unidentifiable
    }

  private def addIdentifierFromText(ontologyType: String,
                                    label: String): IdState.Unminted =
    IdState.Identifiable(
      SierraConceptIdentifier.withNoIdentifier(
        pseudoIdentifier = label,
        ontologyType = ontologyType
      ))

}
