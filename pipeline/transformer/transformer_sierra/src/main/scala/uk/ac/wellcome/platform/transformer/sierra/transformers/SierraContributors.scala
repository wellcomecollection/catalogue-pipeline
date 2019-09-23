package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.transformable.sierra.SierraBibNumber
import uk.ac.wellcome.platform.transformer.sierra.source.{
  MarcSubfield,
  SierraBibData
}

object SierraContributors
    extends SierraTransformer
    with MarcUtils
    with SierraAgents {

  type Output = List[Contributor[MaybeDisplayable[AbstractAgent]]]

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
  def apply(bibId: SierraBibNumber, bibData: SierraBibData) =
    getPersonContributors(bibData, marcTag = "100") ++
      getOrganisationContributors(bibData, marcTag = "110") ++
      getPersonContributors(bibData, marcTag = "700") ++
      getOrganisationContributors(bibData, marcTag = "710")

  private def getPersonContributors(
    bibData: SierraBibData,
    marcTag: String): List[Contributor[MaybeDisplayable[AbstractAgent]]] = {
    val persons = getMatchingSubfields(
      bibData,
      marcTag = marcTag,
      marcSubfieldTags = List("a", "b", "c", "d", "e", "t", "0")
    )

    persons
      .flatMap { subfields: List[MarcSubfield] =>
        val hasSubfieldT = subfields.exists {
          _.tag == "t"
        }
        val roles = getContributionRoles(subfields)

        val maybeAgent = if (hasSubfieldT) {
          getLabel(subfields)
            .map {
              Agent(_)
            }
            .map { agent =>
              identify(subfields, agent, "Agent")
            }
        } else {
          getPerson(subfields, normalisePerson = true)
            .map { person =>
              identify(subfields, person, "Person")
            }
        }

        maybeAgent map { agent =>
          Contributor(
            agent = agent,
            roles = roles
          )
        }
      }
  }

  /* For a given MARC tag (110 or 710), return a list of all the Contributor[Organisation] instances
   * this MARC tag represents.
   */
  private def getOrganisationContributors(
    bibData: SierraBibData,
    marcTag: String): List[Contributor[MaybeDisplayable[Organisation]]] = {
    val organisations = getMatchingSubfields(
      bibData,
      marcTag = marcTag,
      marcSubfieldTags = List("a", "b", "c", "d", "e", "0")
    )

    organisations
      .flatMap { subfields: List[MarcSubfield] =>
        val roles = getContributionRoles(subfields)
        val maybeAgent = getOrganisation(subfields)

        maybeAgent.map { agent =>
          Contributor(
            agent = identify(subfields, agent, "Organisation"),
            roles = roles
          )
        }
      }
  }

  private def getContributionRoles(
    subfields: List[MarcSubfield]): List[ContributionRole] = {
    // Extract the roles from subfield $e.  This is a repeatable field.
    subfields.collect {
      case MarcSubfield("e", content) => ContributionRole(content)
    }
  }
}
