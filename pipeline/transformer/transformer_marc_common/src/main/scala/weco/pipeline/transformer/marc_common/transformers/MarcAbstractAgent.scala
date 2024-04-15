package weco.pipeline.transformer.marc_common.transformers

import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.AbstractAgent
import weco.pipeline.transformer.identifiers.LabelDerivedIdentifiers
import weco.pipeline.transformer.marc_common.models.{MarcField, MarcSubfield}
import weco.pipeline.transformer.text.TextNormalisation.TextNormalisationOps

import scala.util.{Failure, Success, Try}

trait MarcAbstractAgent extends LabelDerivedIdentifiers {
  type Output = Try[AbstractAgent[IdState.Unminted]]

  protected val ontologyType: String
  protected val appropriateFields: Seq[String]
  protected val labelSubfieldTags: Seq[String]
  protected def createAgent(
    label: String,
    identifier: IdState.Unminted
  ): AbstractAgent[IdState.Unminted]

  /** Construct a label from the subfields representing an agent.
    */
  protected def getLabel(field: MarcField): Option[String] = {
    val contents =
      field.subfields
        .filter {
          s => labelSubfieldTags.contains(s.tag)
        }
        .filterNot { _.content.trim.isEmpty }
        .map { _.content }

    contents match {
      case Nil          => None
      case nonEmptyList => Some(nonEmptyList mkString " ")
    }
  }

  protected def normaliseLabel(label: String): String = label.trimTrailing(',')

  /* Given an agent and the associated MARC subfields, look for instances of subfield $0,
   * which are used for identifiers.
   * TODO: UPDATE THIS COMMENT
   * This methods them (if present) and wraps the agent in Unidentifiable or Identifiable
   * as appropriate.
   */
  // TODO: Consider if this might be a trait in its own right.
  //   Does it apply to things that are not agents?
  //   Yes!
  protected def getIdentifier(
    subfields: Seq[MarcSubfield],
    label: String
  ): IdState.Unminted = {

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
      case MarcSubfield("0", content) =>
        content.replaceAll("[.,\\s]", "")
    }

    // If we get exactly one value, we can use it to identify the record.
    // Some records have multiple instances of subfield $0 (it's a repeatable
    // field in the MARC spec).
    codes.distinct match {
      case Seq(code) =>
        IdState.Identifiable(
          SourceIdentifier(
            identifierType = IdentifierType.LCNames,
            value = code,
            ontologyType = ontologyType
          )
        )
      case _ => identifierFromText(label = label, ontologyType = ontologyType)
    }
  }
  private def isAppropriateField(field: MarcField): Boolean =
    appropriateFields.contains(field.marcTag)

  def apply(
    field: MarcField
  ): Try[AbstractAgent[IdState.Unminted]] = {
    if (isAppropriateField(field)) {
      getLabel(field) match {
        case Some(label) =>
          Success(
            createAgent(
              label,
              getIdentifier(
                subfields = field.subfields,
                label = label
              )
            )
          )
        case None =>
          Failure(
            new Exception(
              s"no label found when transforming $field into an $ontologyType"
            )
          )
      }
    } else {
      Failure(
        new Exception(
          s"attempt to transform incompatible MARC field ${field.marcTag} into $ontologyType"
        )
      )
    }
  }
}
