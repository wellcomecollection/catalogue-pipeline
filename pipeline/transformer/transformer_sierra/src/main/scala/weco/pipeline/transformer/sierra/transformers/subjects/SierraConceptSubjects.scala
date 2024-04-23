package weco.pipeline.transformer.sierra.transformers.subjects

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{
  AbstractConcept,
  AbstractRootConcept,
  Concept,
  Place
}
import weco.pipeline.transformer.text.TextNormalisation.TextNormalisationOps
import weco.pipeline.transformer.transformers.ParsedPeriod
//import weco.catalogue.internal_model.work.{Place, _}
import weco.pipeline.transformer.marc_common.models.MarcField
import weco.pipeline.transformer.marc_common.transformers.MarcCommonLabelSubdivisions
//import weco.pipeline.transformer.sierra.transformers.SierraConcepts
//import weco.pipeline.transformer.text.TextNormalisation._
//import weco.pipeline.transformer.transformers.ParsedPeriod
//import weco.sierra.models.identifiers.SierraBibNumber
//import weco.sierra.models.marc.{Subfield, VarField}

import scala.util.{Success, Try}

// Populate wwork:subject
//
// Use MARC field "650", "648" and "651" where the second indicator is not 7 (7 = "Source specified in subfield $2").
//
// - https://www.loc.gov/marc/bibliographic/bd650.html
// - https://www.loc.gov/marc/bibliographic/bd648.html
// - https://www.loc.gov/marc/bibliographic/bd651.html
//
// Within these MARC tags, we have:
//
//    - a primary concept (subfield $a); and
//    - subdivisions (subfields $v, $x, $y and $z)
//
// The primary concept can be identified, and the subdivisions serve
// to add extra context.
//
// We construct the Subject as follows:
//
//    - label is the concatenation of $a, $v, $x, $y and $z in order,
//      separated by a hyphen ' - '.
//    - concepts is a List[Concept] populated in order of the subfields:
//
//        * $a => {Concept, Period, Place}
//          Optionally with an identifier.  We look in subfield $0 for the
//          identifier value, then second indicator for the authority.
//          These are decided as follows:
//
//            - 650 => Concept
//            - 648 => Period
//            - 651 => Place
//
//        * $v => Concept
//        * $x => Concept
//        * $y => Period
//        * $z => Place
//
//      Note that only concepts from subfield $a are identified; everything
//      else is unidentified.
//
object SierraConceptSubjects
    extends SierraSubjectsTransformer
    with MarcCommonLabelSubdivisions {

  val subjectVarFields = List("650", "648", "651")
  override protected val labelSubfields: Seq[String] = Nil
  override protected val ontologyType: String = ""

  override protected def getLabel(field: MarcField): Option[String] = {
    val (primary, secondary) = getLabelSubfields(field)
    Option(getLabel(primary, secondary)).filter(_.nonEmpty)
  }
  override protected def getIdState(field: MarcField): IdState.Unminted =
    super.getIdState(field, getFieldOntologyType(field))
  override def getSubjectConcepts(
    field: MarcField
  ): Try[Seq[AbstractRootConcept[IdState.Unminted]]] = {

    getLabelSubfields(field) match {
      case (_, Nil) =>
        val wholeFieldId = getIdState(field) match {
          case identifiable: IdState.Identifiable => Some(identifiable)
          case _                                  => None
        }
        Success(getPrimaryTypeConcepts(field, wholeFieldId))
      case _ =>
        Success(
          getPrimaryTypeConcepts(field, None) ++ getSubdivisions(
            field
          )
        )
    }

  }

  /** Return AbstractConcepts of the appropriate subtype for this field.
    *
    * A Concept Subject MARC field should contain exactly one $a subfields, but
    * due to third-party cataloguing errors, may contain more.
    *
    * The $a subfield contains a term whose type is derived from the overall
    * field, so any $a subfields in a "Subject Added Entry-Chronological Term"
    * will be a Period, etc.
    *
    * $a is a non-repeatable subfield, so you would expect primarySubfields to
    * be a single value, and for this to return a single value. However, some
    * records that are received from third-party organisations do erroneously
    * contain multiple $a subfields. This transformer will accept them and
    * produce the appropriate concepts.
    */
  private def getPrimaryTypeConcepts(
    field: MarcField,
    idstate: Option[IdState.Identifiable]
  ): Seq[AbstractConcept[IdState.Unminted]] =
    field.subfields.filter(_.tag == "a").map {
      subfield =>
        val label = subfield.content.trimTrailingPeriod
        field.marcTag match {
          case "650" => Concept(label = label).normalised.identifiable(idstate)
          case "648" => ParsedPeriod(label = label).identifiable(idstate)
          case "651" => Place(label = label).normalised.identifiable(idstate)
        }
    }

  private def getFieldOntologyType(field: MarcField): String =
    field.marcTag match {
      case "650" => "Concept"
      case "648" => "Period"
      case "651" => "Place"
    }
}
