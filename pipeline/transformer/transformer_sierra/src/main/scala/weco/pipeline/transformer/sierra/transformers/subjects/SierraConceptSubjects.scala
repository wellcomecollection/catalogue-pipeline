package weco.pipeline.transformer.sierra.transformers.subjects

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work._
import weco.pipeline.transformer.sierra.transformers.SierraConcepts
import weco.pipeline.transformer.text.TextNormalisation._
import weco.pipeline.transformer.transformers.ParsedPeriod
import weco.sierra.models.identifiers.SierraBibNumber
import weco.sierra.models.marc.{Subfield, VarField}

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
    with SierraConcepts {

  val subjectVarFields = List("650", "648", "651")

  def getSubjectsFromVarFields(bibId: SierraBibNumber,
                               varfields: List[VarField]): Output = {
    // Second indicator 7 means that the subject authority is something other
    // than library of congress or mesh. Some MARC records have duplicated subjects
    // when the same subject has more than one authority (for example mesh and FAST),
    // which causes duplicated subjects to appear in the API.
    //
    // Example from b10199135 (j7jm24hj)
    //  650  2 Retina|xphysiology.|0D012160Q000502
    //  650  2 Vision, Ocular.|0D014785
    //  650  2 Visual Pathways.|0D014795
    //  650  7 Retina.|2fast|0(OCoLC)fst01096191
    //  650  7 Vision.|2fast|0(OCoLC)fst01167852
    //
    //
    // So let's filter anything that is from another authority for now.
    varfields.filterNot(_.indicator2.contains("7")).map { varfield =>
      val subfields = varfield.subfieldsWithTags("a", "v", "x", "y", "z")
      val (primarySubfields, subdivisionSubfields) = subfields.partition {
        _.tag == "a"
      }

      val label = getLabel(primarySubfields, subdivisionSubfields)
      val concepts: List[AbstractConcept[IdState.Unminted]] = getPrimaryConcept(
        primarySubfields,
        varField = varfield) ++ getSubdivisions(subdivisionSubfields)

      Subject(
        id = identifyConcept(ontologyType = "Subject", varfield),
        label = label,
        concepts = concepts
      )
    }
  }

  private def getPrimaryConcept(
    primarySubfields: List[Subfield],
    varField: VarField): List[AbstractConcept[IdState.Unminted]] =
    primarySubfields.map { subfield =>
      val label = subfield.content.trimTrailingPeriod

      varField.marcTag.get match {
        case "650" => Concept(label = label)
        case "648" => ParsedPeriod(label = label)
        case "651" => Place(label = label)
      }
    }
}
