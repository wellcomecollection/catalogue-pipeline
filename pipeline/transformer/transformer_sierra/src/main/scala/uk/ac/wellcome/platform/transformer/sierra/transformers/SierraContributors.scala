package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.transformable.sierra.SierraBibNumber
import uk.ac.wellcome.platform.transformer.sierra.source.{
  MarcSubfield,
  SierraBibData,
  SierraQueryOps,
}

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
    extends SierraTransformer
    with SierraQueryOps
    with SierraAgents {

  type Output = List[Contributor[Unminted[AbstractAgent]]]

  val contributorFields = List(
    ("100", getPersonContributors _, "e"),
    ("110", getOrganisationContributors _, "e"),
    ("111", getMeetingContributors _, "j"),
    ("700", getPersonContributors _, "e"),
    ("710", getOrganisationContributors _, "e"),
    ("711", getMeetingContributors _, "j"),
  )

  def apply(bibId: SierraBibNumber, bibData: SierraBibData) =
    contributorFields.flatMap {
      case (tag, f, roleTag) =>
        bibData
          .varfieldsWithTag(tag)
          .flatMap { varfield =>
            val (ontologyType, maybeAgent) = f(varfield.subfields)
            maybeAgent.map { agent =>
              Contributor(
                agent = identify(varfield.subfields, agent, ontologyType),
                roles = getContributionRoles(varfield.subfields, roleTag)
              )
            }
          }
    }

  private def getPersonContributors(subfields: List[MarcSubfield]) =
    if (subfields.withTags("t").isEmpty)
      "Person" -> getPerson(subfields, true)
    else
      "Agent" -> getLabel(subfields).map(Agent(_))

  private def getOrganisationContributors(subfields: List[MarcSubfield]) =
    "Organisation" -> getOrganisation(subfields)

  private def getMeetingContributors(subfields: List[MarcSubfield]) =
    "Meeting" -> getMeeting(subfields)

  private def getContributionRoles(
    subfields: List[MarcSubfield],
    subfieldTag: String): List[ContributionRole] =
    subfields
      .withTag(subfieldTag)
      .contents
      .map(ContributionRole(_))
}
