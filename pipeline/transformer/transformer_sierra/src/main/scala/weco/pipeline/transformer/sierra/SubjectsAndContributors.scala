package weco.pipeline.transformer.sierra

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.{Contributor, Subject}

object ConceptTypeHarmoniser extends Logging {

  /**
    * Given lists of subjects and contributors, ensure that any top-level Concepts
    * that have the same id and id type also have the same ontologyType.
    *
    * This only operates on top-level Concepts within subjects and contributors, because it is only the top-level
    * Concept that can have an external id, and therefore only those Concepts can be reliably determined to refer the same thing.
    *
    * The ontologyType of a Concept (or Agent) can only be estimated from its usage in MARC - the MARC field or subfield it is in
    * determines the type we assign to it in the corresponding transformer. However, this estimate is not 100% reliable.
    *
    * Two scenarios that are widespread in the data are:
    *  - Places used in a Subject field are also used in the Contributor field as an Organisation (because places do not have agency)
    *  - A Person contributor is duplicated with extra subfields that make the transformer interpret it as a non-specific Agent.
    *
    * In both of these scenarios, the data in a single field alone is not sufficient to distinguish between a legitimate
    * Organisation or Agent from a Place or Person.
    *
    * This function looks ot the collection of Concepts as a whole.  When there is a conflict, it makes an educated guess
    * as to which of the Concepts is more likely to be correct, and replaces the ontologyType in the one that is less likely
    * to be correct to match it.
    *
    * The two rules for determining likely correctness are:
    *  - more specific over less specific (Person > Agent)
    *     - you have told me specifically what kind of thing this is over there, no need to hedge what it is over here.
    *  - subject over contributor (Place > Organisation in the scenario described above)
    *     - extracting type from subject is more reliable, trust that definition more than this one.
    */
  def apply(
    subjects: List[Subject[IdState.Unminted]],
    contributors: List[Contributor[IdState.Unminted]]
  ): (List[Subject[IdState.Unminted]], List[Contributor[IdState.Unminted]]) = {
    val harmonisedSubjects = subjects
      .groupBy(
        // Only Items (which are not Concepts) have ids with more than one sourceidentifier
        subject =>
          typeFreeSourceIdentifer(subject.id.allSourceIdentifiers.headOption)
      )
      .flatMap {
        // no identifiers, nothing to do.
        case (None, subjects) => subjects
        //Only one member of the group, nothing to replace
        case (_, Seq(head)) => List(head)
        case (_, subjects) =>
          harmoniseSubjectTypes(
            subjects.asInstanceOf[List[Subject[IdState.Identifiable]]]
          )
      }
      .toList
      .distinct
    (harmonisedSubjects, contributors)
  }

  private def typeFreeSourceIdentifer(
    sourceIdentifierOption: Option[SourceIdentifier]
  ): Option[(IdentifierType, String)] =
    sourceIdentifierOption flatMap { sourceIdentifier =>
      Some((sourceIdentifier.identifierType, sourceIdentifier.value))
    }

  /**
    * Within a subjects list, harmonise on the "most specific" type.
    */
  private def harmoniseSubjectTypes(
    subjects: List[Subject[IdState.Identifiable]]
  ): List[Subject[IdState.Identifiable]] = {
    // return a copy of all the subjects, with the new type inserted.
    val bestType = mostSpecificType(
      subjects.map(_.id.sourceIdentifier.ontologyType)
    )
    subjects map { subject =>
      subject.copy(
        id = subject.id
          .copy(
            sourceIdentifier =
              subject.id.sourceIdentifier.copy(ontologyType = bestType)
          )
      )
    }
  }

  private def mostSpecificType(types: List[String]): String = {
    //
    // Filter out the vague types.
    val specificTypes = types.filterNot(Seq("Agent", "Concept").contains(_))
    specificTypes match {
      // if there are no more specific types, Agent is more specific than Concept.
      case Nil => if (types.contains("Agent")) "Agent" else "Concept"
      // Ideally, the specific list will have only one entry.
      case List(bestType) => bestType
      // If not, log if there are more than one, and return the head.
      case listOfTypes =>
        info(
          s"Multiple specific types encountered for the same id: $specificTypes, choosing $listOfTypes.head"
        )
        listOfTypes.head
    }
  }

}
