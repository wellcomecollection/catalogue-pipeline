package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.transformable.sierra.SierraBibNumber
import uk.ac.wellcome.platform.transformer.sierra.source.{
  MarcSubfield,
  SierraBibData,
  SierraQueryOps,
}

object SierraContributors
    extends SierraTransformer
    with SierraQueryOps
    with SierraAgents {

  type Output = List[Contributor[MaybeDisplayable[AbstractAgent]]]

  val contributorFields = List(
    "100" -> getPersonContributors _,
    "110" -> getOrganisationContributors _,
    "111" -> getMeetingContributors _,
    "700" -> getPersonContributors _,
    "710" -> getOrganisationContributors _,
    "711" -> getMeetingContributors _,
  )

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
    contributorFields.flatMap { case (tag, f) => f(bibData, tag) }

  private def getPersonContributors(
    bibData: SierraBibData,
    marcTag: String): List[Contributor[MaybeDisplayable[AbstractAgent]]] =
    getMatchingSubfieldLists(
      bibData,
      marcTag = marcTag,
      marcSubfieldTags = List("a", "b", "c", "d", "e", "t", "0")
    ).flatMap { subfields: List[MarcSubfield] =>
      val hasSubfieldT = subfields.exists {
        _.tag == "t"
      }
      val roles = getContributionRoles(subfields, "e")

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

  /* For a given MARC tag (110 or 710), return a list of all the Contributor[Organisation] instances
   * this MARC tag represents.
   */
  private def getOrganisationContributors(
    bibData: SierraBibData,
    marcTag: String): List[Contributor[MaybeDisplayable[Organisation]]] =
    getMatchingSubfieldLists(
      bibData,
      marcTag = marcTag,
      marcSubfieldTags = List("a", "b", "c", "d", "e", "0")
    ).flatMap { subfields: List[MarcSubfield] =>
      val roles = getContributionRoles(subfields, "e")
      val maybeAgent = getOrganisation(subfields)

      maybeAgent.map { agent =>
        Contributor(
          agent = identify(subfields, agent, "Organisation"),
          roles = roles
        )
      }
    }

  private def getMeetingContributors(
    bibData: SierraBibData,
    marcTag: String): List[Contributor[MaybeDisplayable[Meeting]]] = {
    getMatchingSubfieldLists(
      bibData,
      marcTag = marcTag,
      marcSubfieldTags = List("a", "c", "d", "j", "t", "0")
    ).flatMap { subfields: List[MarcSubfield] =>
      val roles = getContributionRoles(subfields, "j")
      val maybeAgent = getMeeting(subfields)

      maybeAgent.map { agent =>
        Contributor(
          agent = identify(subfields, agent, "Meeting"),
          roles = roles
        )
      }
    }
  }

  private def getContributionRoles(
    subfields: List[MarcSubfield],
    subfieldTag: String): List[ContributionRole] =
    // Extract the roles from subfield.  This is a repeatable field.
    subfields.collect {
      case MarcSubfield(tag, content) if tag == subfieldTag =>
        ContributionRole(content)
    }

  private def getMatchingSubfieldLists(
    bibData: SierraBibData,
    marcTag: String,
    marcSubfieldTags: List[String]): List[List[MarcSubfield]] =
    bibData
      .varfieldsWithTag(marcTag)
      .collect {
        case varfield =>
          varfield.subfieldsWithTags(marcSubfieldTags: _*)
      }
}
