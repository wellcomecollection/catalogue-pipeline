package weco.pipeline.transformer.sierra.transformers

import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work._
import weco.pipeline.transformer.identifiers.LabelDerivedIdentifiers
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
    with SierraAgents
    with LabelDerivedIdentifiers {

  type Output = List[Contributor[IdState.Unminted]]

  import OntologyTypeOps._

  case class ContributorField(
    marcTag: String,
    roleTags: Seq[String],
    isPrimary: Boolean,
    getContributors: List[Subfield] => (
      String,
      Option[AbstractAgent[IdState.Unminted]]
    )
  )

  // Quoting an email from Louise Grainger, dated 25 August 2022:
  //
  //      100 – we want valid subfield $e (relator/contributor), but NOT valid $j (attribution qualifier)
  //          – if no $e do not display alternative
  //
  //      110 – we want valid subfield $e (relator/contributor); $j is invalid in this field
  //          – if no $e do not display alternative
  //
  //      111 – we want valid subfield $j (relator/contributor); $e is invalid in this field
  //          – if no $j do not display alternative
  //
  //      700 – we want valid subfield $e (relator/contributor) AND valid subfield $j (attribution qualifier)
  //          – if no $e then display $j; if $e then (ideally) display $j too
  //
  //      710 - we want valid subfield $e (relator/contributor); $j is invalid in this field
  //          – if no $e do not display alternative
  //
  //      711 - we want valid subfield $j (relator/contributor); $e is invalid in this field
  //      – if no $j do not display alternative
  //
  val contributorFields = List(
    ContributorField(
      marcTag = "100",
      roleTags = Seq("e"),
      isPrimary = true,
      getPersonContributors
    ),
    ContributorField(
      marcTag = "110",
      roleTags = Seq("e"),
      isPrimary = true,
      getOrganisationContributors
    ),
    ContributorField(
      marcTag = "111",
      roleTags = Seq("j"),
      isPrimary = true,
      getMeetingContributors
    ),
    ContributorField(
      marcTag = "700",
      roleTags = Seq("e", "j"),
      isPrimary = false,
      getPersonContributors
    ),
    ContributorField(
      marcTag = "710",
      roleTags = Seq("e"),
      isPrimary = false,
      getOrganisationContributors
    ),
    ContributorField(
      marcTag = "711",
      roleTags = Seq("j"),
      isPrimary = false,
      getMeetingContributors
    )
  )

  def apply(bibData: SierraBibData): List[Contributor[IdState.Unminted]] = {
    val allContributors = contributorFields.flatMap {
      case ContributorField(marcTag, roleTags, isPrimary, getContributors) =>
        bibData
          .varfieldsWithTag(marcTag)
          .flatMap {
            varfield =>
              val (ontologyType, maybeAgent) =
                getContributors(varfield.subfields)
              maybeAgent.map {
                agent =>
                  Contributor(
                    agent = withId(
                      agent,
                      identify(varfield.subfields, ontologyType, agent.label)
                    ),
                    roles = getContributionRoles(varfield.subfields, roleTags),
                    primary = isPrimary
                  )
              }
          }
    }

    // We need to remove duplicates where two contributors differ only
    // by primary/not-primary
    val duplicatedContributors =
      allContributors
        .filter(_.primary == false)
        .filter(c => allContributors.contains(c.copy(primary = true)))
        .toSet

    allContributors
      .filterNot(c => duplicatedContributors.contains(c))
      .harmoniseOntologyTypes
  }
  private def getPersonContributors(
    subfields: List[Subfield]
  ): (String, Option[AbstractAgent[IdState.Unminted]]) =
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
    roleTags: Seq[String]
  ): List[ContributionRole] =
    subfields
      .withTags(roleTags: _*)
      .contents
      .map {
        role =>
          // The contribution role in the raw MARC data sometimes includes a
          // trailing full stop, because all the subfields are meant to be concatenated
          // into a single sentence.
          //
          // This full stop doesn't make sense in a structured field, so remove it.
          role.stripSuffix(".")
      }
      .map(ContributionRole)

  private def withId(
    agent: AbstractAgent[IdState.Unminted],
    id: IdState.Unminted
  ) =
    agent match {
      case a: Agent[IdState.Unminted]        => a.copy(id = id)
      case p: Person[IdState.Unminted]       => p.copy(id = id)
      case o: Organisation[IdState.Unminted] => o.copy(id = id)
      case m: Meeting[IdState.Unminted]      => m.copy(id = id)
    }

  /* Given an agent and the associated MARC subfields, look for instances of subfield $0,
   * which are used for identifiers.
   *
   * This methods them (if present) and wraps the agent in Unidentifiable or Identifiable
   * as appropriate.
   */
  private def identify(
    subfields: List[Subfield],
    ontologyType: String,
    label: String
  ): IdState.Unminted = {

    // We take the contents of subfield $0.  They may contain inconsistent
    // spacing and punctuation, such as:
    //
    //    " nr 82270463"
    //    "nr 82270463"
    //    "nr 82270463.,"
    //
    // which all refer to the same identifier.
    //
    // For consistency, we remove all whitespace and some punctuation
    // before continuing.
    val codes = subfields.collect {
      case Subfield("0", content) =>
        content.replaceAll("[.,\\s]", "")
    }

    // If we get exactly one value, we can use it to identify the record.
    // Some records have multiple instances of subfield $0 (it's a repeatable
    // field in the MARC spec).
    codes.distinct match {
      case Seq(code) =>
        IdState.Identifiable(
          SourceIdentifier(
            identifierType = IdentifierType.LCNames,
            value = code,
            ontologyType = ontologyType
          )
        )
      case _ => identifierFromText(label = label, ontologyType = ontologyType)
    }
  }
}
