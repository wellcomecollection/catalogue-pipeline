package weco.pipeline.transformer.sierra

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{Contributor, Subject}
import weco.pipeline.transformer.sierra.transformers.{
  SierraContributors,
  SierraSubjects
}
import weco.pipeline.transformer.sierra.transformers.OntologyTypeOps._
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
    * This object looks ot the collection of Concepts from these two locations as a whole.
    * When there is a conflict, it makes an educated guess as to which of the Concepts is more likely to be correct,
    * and replaces the ontologyType in the one that is less likely to be correct to match it.
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
    (
      subjects,
      contributors.harmoniseOntologyTypesWith(subjects.bestOntologyTypes)
    )
  }

}
