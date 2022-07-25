package weco.pipeline.transformer.sierra.transformers

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work._
import weco.sierra.models.SierraQueryOps
import weco.sierra.models.data.SierraBibData
import weco.sierra.models.marc.Subfield

/* Populate wwork:contributors. Rules:
 *
 * For bib records with MARC tag 100 or 700, create a "Person":
 *
 * For bib records with MARC tag 110 or 710, create an "Organisation".
 *
 * For Persons and Organisations, subfield $e is used for the labels in "roles".
 *
 * Note: for MARC tag 700, we want to type as "Agent" rather than "Person"
 * if there's a subfield "t", as this may indicate something more specific.
 * e.g. some MARC records have "Hamlet", the fictional character as a 700 entry.
 * We'll add a more specific type later, but "Person" isn't appropriate.
 *
 * Order by MARC tag (100, 110, 700, 710), then by order of appearance
 * in the MARC data.
 *
 * https://www.loc.gov/marc/bibliographic/bd100.html
 * https://www.loc.gov/marc/bibliographic/bd110.html
 * https://www.loc.gov/marc/bibliographic/bd700.html
 * https://www.loc.gov/marc/bibliographic/bd710.html
 *
 */
object SierraContributors
    extends SierraDataTransformer
    with SierraQueryOps
    with SierraAgents {

  type Output = List[Contributor[IdState.Unminted]]

  case class ContributorField(
    marcTag: String,
    roleTag: String,
    isPrimary: Boolean,
    getContributors: List[Subfield] => (String, Option[AbstractAgent[IdState.Unminted]]),
  )

  val contributorFields = List(
    ContributorField(marcTag = "100", roleTag = "e", isPrimary = true, getPersonContributors),
    ContributorField(marcTag = "110", roleTag = "e", isPrimary = true, getOrganisationContributors),
    ContributorField(marcTag = "111", roleTag = "j", isPrimary = true, getMeetingContributors),
    ContributorField(marcTag = "700", roleTag = "e", isPrimary = false, getPersonContributors),
    ContributorField(marcTag = "710", roleTag = "e", isPrimary = false, getOrganisationContributors),
    ContributorField(marcTag = "711", roleTag = "j", isPrimary = false, getMeetingContributors),
  )

  def apply(bibData: SierraBibData): List[Contributor[IdState.Unminted]] = {
    val allContributors = contributorFields.flatMap {
      case ContributorField(marcTag, roleTag, isPrimary, getContributors) =>
        bibData
          .varfieldsWithTag(marcTag)
          .flatMap { varfield =>
            val (ontologyType, maybeAgent) = getContributors(varfield.subfields)
            maybeAgent.map { agent =>
              Contributor(
                agent =
                  withId(agent, identify(varfield.subfields, ontologyType)),
                roles = getContributionRoles(varfield.subfields, roleTag),
                primary = isPrimary
              )
            }
          }
    }.distinct

    // We need to remove duplicates where two contributors differ only
    // by primary/not-primary
    val duplicatedContributors =
      allContributors
        .filter(_.primary == false)
        .filter(c => allContributors.contains(c.copy(primary = true)))
        .toSet

    allContributors.filterNot(c => duplicatedContributors.contains(c))
  }

  private def getPersonContributors(subfields: List[Subfield]): (String, Option[AbstractAgent[IdState.Unminted]]) =
    if (subfields.withTags("t").isEmpty)
      "Person" -> getPerson(subfields, normalisePerson = true)
    else
      "Agent" -> getLabel(subfields).map(Agent(_))

  private def getOrganisationContributors(subfields: List[Subfield]) =
    "Organisation" -> getOrganisation(subfields)

  private def getMeetingContributors(subfields: List[Subfield]) =
    "Meeting" -> getMeeting(subfields)

  private def getContributionRoles(
    subfields: List[Subfield],
    subfieldTag: String): List[ContributionRole] =
    subfields
      .withTag(subfieldTag)
      .contents
      .map { role =>
        // The contribution role in the raw MARC data sometimes includes a
        // trailing full stop, because all the subfields are meant to be concatenated
        // into a single sentence.
        //
        // This full stop doesn't make sense in a structured field, so remove it.
        role.stripSuffix(".")
      }
      .map(ContributionRole)

  private def withId(agent: AbstractAgent[IdState.Unminted],
                     id: IdState.Unminted) =
    agent match {
      case a: Agent[IdState.Unminted]        => a.copy(id = id)
      case p: Person[IdState.Unminted]       => p.copy(id = id)
      case o: Organisation[IdState.Unminted] => o.copy(id = id)
      case m: Meeting[IdState.Unminted]      => m.copy(id = id)
    }
}
