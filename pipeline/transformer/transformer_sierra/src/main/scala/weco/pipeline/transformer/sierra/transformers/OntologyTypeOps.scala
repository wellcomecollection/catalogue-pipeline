package weco.pipeline.transformer.sierra.transformers

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.identifiers.{IdState, SourceIdentifier}
import weco.catalogue.internal_model.work.{
  AbstractRootConcept,
  Contributor,
  Subject
}

object OntologyTypeOps extends Logging {

  implicit class SubjectListOps(entries: List[Subject[IdState.Unminted]])
      extends EntityWithOntologyTypeList[Subject[IdState.Unminted]] {
    lazy val wrappedEntries: List[SubjectWithOntologyType] =
      entries.map(SubjectWithOntologyType)
  }

  implicit class ContributorListOps(
    entries: List[Contributor[IdState.Unminted]]
  ) extends EntityWithOntologyTypeList[Contributor[IdState.Unminted]] {
    lazy val wrappedEntries: List[ContributorWithOntologyType] =
      entries.map(ContributorWithOntologyType)
  }

  sealed trait EntityWithOntologyType[Output] {
    val original: Output

    /**
      * Return the principal source identifier associated with the entity in question.
      */
    def mainSourceIdentifier: Option[SourceIdentifier]

    /**
      *  Return a copy of the entity in question, with its ontologyType corrected to newType
      */
    def copyWithNewType(newType: String): Output

    /**
      * Return the sourceIdentifier of the wrapped object with its ontologytype blanked.
      */
    def typeFreeSourceIdentifier: Option[SourceIdentifier] =
      mainSourceIdentifier flatMap { sourceIdentifier =>
        Some(sourceIdentifier.copy(ontologyType = ""))
      }
  }

  case class SubjectWithOntologyType(original: Subject[IdState.Unminted])
      extends EntityWithOntologyType[Subject[IdState.Unminted]] {

    /**
      * Return the subject's sourceIdentifer
      * Subjects have their own SourceIdentifier.
      */
    def mainSourceIdentifier: Option[SourceIdentifier] =
      // allSourceIdentifiers returns a list, but subjects are expected to have only one
      // sourceIdentifier of their own.
      original.id.allSourceIdentifiers.headOption

    /**
      * Return a copy of the Subject, with its ontologyType changed to newType.
      *
      * In a Subject, the ontologyType needs to be changed in up to two places.
      * The subject's own sourceIdentifier, and, only in the case simple subjects
      * the sole Concept in the Subject's concepts list
      *
      * In a compound subject, the Concepts in the concepts list will all be label-derived
      * and do not have an authoritative identifier to match against, so cannot be unified.
      */
    def copyWithNewType(newType: String): Subject[IdState.Unminted] = {
      // This should never be called with an unidentifiable subject, because the
      // point of this is to fix references that have been identified, but have the wrong type,
      // so let this error out if called on a subject with an inappropriate state
      val identifiable =
        original.asInstanceOf[Subject[IdState.Identifiable]]
      val newSourceIdentifier =
        mainSourceIdentifier.get.copy(ontologyType = newType)
      identifiable
        .copy(
          id = identifiable.id
            .copy(
              sourceIdentifier = newSourceIdentifier
            ),
          concepts =
            if (identifiable.concepts.length == 1) {
              val oldConcept = identifiable.concepts.head
              List(
                AbstractRootConcept(
                  newType,
                  oldConcept.id.copy(sourceIdentifier = newSourceIdentifier),
                  oldConcept.label
                )
              )
            } else
              identifiable.concepts
        )
    }
  }

  case class ContributorWithOntologyType(
    original: Contributor[IdState.Unminted]
  ) extends EntityWithOntologyType[Contributor[IdState.Unminted]] {

    /**
      * Return the contributor's Agent's sourceIdentifier.
      * Contributors do not have a sourceIdentifier of their own, so the identifier is that of
      * whatever resides within its agent property
      * (Contributor _does_ extend HasId, but is always unidentifiable)
      */
    def mainSourceIdentifier: Option[SourceIdentifier] =
      original.agent.id.allSourceIdentifiers.headOption

    /**
      * Return a copy of this Contributor with the Agent having been assigned the correct type.
      */
    def copyWithNewType(newType: String): Contributor[IdState.Unminted] = {
      // This should never be called with an unidentifiable agent, because the
      // point of this is to fix references that have been identified, but have the wrong type,
      // so let this error out if called on a subject with an inappropriate state
      val oldAgent =
        original.agent
          .asInstanceOf[AbstractRootConcept[IdState.Identifiable]]
      val newSourceIdentifier =
        mainSourceIdentifier.get.copy(ontologyType = newType)
      original.copy(
        agent = AbstractRootConcept(
          newType,
          oldAgent.id.copy(sourceIdentifier = newSourceIdentifier),
          oldAgent.label
        )
      )
    }
  }

  trait EntityWithOntologyTypeList[T] {
    val wrappedEntries: List[EntityWithOntologyType[T]]

    lazy val bestOntologyTypes: Map[Option[SourceIdentifier], String] =
      makeOntologyTypeMap(wrappedEntries)

    def harmoniseOntologyTypesWith(
      idsToOntologyTypes: Map[Option[SourceIdentifier], String]
    ): List[T] = {
      val harmonisedEntries: List[T] = wrappedEntries map { entry =>
        idsToOntologyTypes.get(entry.typeFreeSourceIdentifier) match {
          case None => entry.original
          case Some(bestType) =>
            entry.copyWithNewType(
              bestType
            )
        }
      }
      harmonisedEntries.distinct
    }

    def harmoniseOntologyTypes: List[T] =
      harmoniseOntologyTypesWith(bestOntologyTypes)
  }

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
        warn(
          s"Multiple specific types encountered for the same id: $specificTypes, choosing $listOfTypes.head"
        )
        listOfTypes.head
    }
  }

  /**
    * Generate a map of untyped (ontology-wise) sourceIdentifiers to the most appropriate ontology type available.
    *
    * In any given document, there may be multiple objects that have the same real-world referent.
    * These are specified with the same authoritative identifier, but because the ontologyTypes have
    * been derived from the field in which it was found, they will have different SourceIdentifiers.
    *
    * This map provides a way to correct these SourceIdentifiers
    * (or at least unify them to the type that seems most likely to be correct)
    */
  private def makeOntologyTypeMap[T](
    entries: List[EntityWithOntologyType[T]]
  ): Map[Option[SourceIdentifier], String] = {
    entries
      .groupBy(_.typeFreeSourceIdentifier)
      .flatMap {
        case (None, _) => None
        case (identifier, values) =>
          val bestType = mostSpecificType(
            values.map(
              value => value.mainSourceIdentifier.get.ontologyType
            )
          )
          Some(identifier -> bestType)
      }
  }

}
