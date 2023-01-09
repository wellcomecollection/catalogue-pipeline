package weco.pipeline.transformer.sierra

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.{
  AbstractRootConcept,
  Agent,
  Concept,
  Contributor,
  Meeting,
  Organisation,
  Period,
  Person,
  Place,
  Subject
}
import weco.pipeline.transformer.sierra.transformers.{
  SierraContributors,
  SierraSubjects
}
import weco.sierra.models.data.SierraBibData
import weco.sierra.models.identifiers.SierraBibNumber

object SubjectsAndContributors extends Logging {

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
    bibId: SierraBibNumber,
    bibData: SierraBibData
  ): (List[Subject[IdState.Unminted]], List[Contributor[IdState.Unminted]]) =
    SubjectsAndContributors(
      subjects = SierraSubjects(bibId, bibData),
      contributors = SierraContributors(bibData)
    )

  def apply(
    subjects: List[Subject[IdState.Unminted]],
    contributors: List[Contributor[IdState.Unminted]]
  ): (List[Subject[IdState.Unminted]], List[Contributor[IdState.Unminted]]) = {
    val fixedSubjects = harmonisedSubjects(subjects)
    (fixedSubjects, contributors)
  }

  /**
    * Given a list of subjects, ensure that any of them that have the same sourceIdentifier type and value
    * also have the same ontologytype in the sourceidentifier.
    *
    * In a Subject, the Subject itself represents a Concept, and it also contains a list of Concepts.
    *
    * When the subject is a compound consisting of more than one constituent concept, those constituents
    * are only derived from the label, so there is nothing to do to them here.
    *
    * A simple subject, consisting of only one constituent concept will also need to have that concept
    * harmonised.
    */
  private def harmonisedSubjects(
    subjects: List[Subject[IdState.Unminted]]
  ): List[Subject[IdState.Unminted]] = {
    subjects
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
  }

  /**
    * Return the sourceIdentifier information, ignoring its ontologytype.
    *
    * As this is only used to perform a type-insensitive match, a tuple of the values will do.
    *
    * //TODO: should this be a property of SourceIdentifier?
    */
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
      val newSourceIdentifier =
        subject.id.sourceIdentifier.copy(ontologyType = bestType)
      subject.copy(
        id = subject.id
          .copy(
            sourceIdentifier = newSourceIdentifier
          ),
        concepts = if (subject.concepts.length == 1) {
          val oldConcept = subject.concepts.head
          List(
            conceptTypeMap(bestType)(
              oldConcept.id.copy(sourceIdentifier = newSourceIdentifier),
              oldConcept.label
            )
          )
        } else subject.concepts
      )
    }
  }

  private val conceptTypeMap
    : Map[String, (IdState.Identifiable, String) => AbstractRootConcept[
      IdState.Identifiable
    ]] = Map(
    "Concept" -> (new Concept[IdState.Identifiable](_, _)),
    "Agent" -> (new Agent[IdState.Identifiable](_, _)),
    "Place" -> (new Place[IdState.Identifiable](_, _)),
    "Period" -> (new Period[IdState.Identifiable](_, _)),
    "Meeting" -> (new Meeting[IdState.Identifiable](_, _)),
    "Organisation" -> (new Organisation[IdState.Identifiable](_, _)),
    "Person" -> (new Person[IdState.Identifiable](_, _))
  )

  /**
    * Given a list of Abstract Concept Ontology Types, return the most specific one from the list
    *
    * Given that there isn't a linear hierarchy of all types, the actual algorithm for this is fairly crude.
    * There are two "base" or "vague" types, and all the others are equally as "specific".
    * In real data, this function will not be called for many documents, and when it is, it
    * will almost always be to resolve disharmony between one of the vague types and one more specific one.
    */
  private def mostSpecificType(types: List[String]): String = {
    // Filter out the vague types.
    val specificTypes = types.filterNot(Seq("Agent", "Concept").contains(_))
    specificTypes match {
      // if there are no more specific types, Agent is more specific than Concept.
      case Nil => if (types.contains("Agent")) "Agent" else "Concept"
      // Ideally, and in most cases, the specific list will have only one entry.
      case List(bestType) => bestType
      // If not, log that there are more than one, and return the head.
      case listOfTypes =>
        info(
          s"Multiple specific types encountered for the same id: $specificTypes, choosing $listOfTypes.head"
        )
        listOfTypes.head
    }
  }

}
