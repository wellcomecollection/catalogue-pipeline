package weco.pipeline.transformer.marc_common.transformers

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.Contributor

import weco.pipeline.transformer.marc_common.logging.LoggingContext
import weco.pipeline.transformer.marc_common.models.{MarcField, MarcRecord}

import scala.util.{Failure, Success}

/* Populate wwork:contributors. Rules:
 *
 * A contributor may be primary (1xx) or not (7xx)
 * A contributor may be a person (x00), organisation (x10) or meeting (x11)
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

object MarcContributors
    extends MarcDataTransformerWithLoggingContext
    with Logging {
  import weco.pipeline.transformer.marc_common.OntologyTypeOps._

  type Output = Seq[Contributor[IdState.Unminted]]

  def apply(record: MarcRecord)(implicit ctx: LoggingContext): Output = {
    val primaries = record
      .fieldsWithTags("100", "110", "111")
    val secondaries = record.fieldsWithTags("700", "710", "711")
    filterSecondaryDuplicates(
      (primaries ++ secondaries)
        .flatMap(field => singleContributor(field))
    ).toList.harmoniseOntologyTypes
  }
  private def isPrimary(marcTag: String): Boolean =
    marcTag.startsWith("1")

  /** Remove non-primary contributors who are also present as primary
    * contributors
    *
    * It is possible that the input MARC may have some contributors who are
    * mentioned as both primary (1xx - Main Entry...) and non-primary (7xx -
    * Added Entry...) contributors.
    *
    * We do not want this duplication in the output.
    */
  private def filterSecondaryDuplicates(allContributors: Output): Output = {
    val duplicatedContributors =
      allContributors
        .filter(_.primary == false)
        .filter(c => allContributors.contains(c.copy(primary = true)))
        .toSet

    allContributors
      .filterNot(c => duplicatedContributors.contains(c))
  }
  private def singleContributor(
    field: MarcField
  )(implicit ctx: LoggingContext): Option[Contributor[IdState.Unminted]] = {
    (field.marcTag.substring(1) match {
      // Some "Person" entries cannot be reliably determined to be an actual
      // for-real-life Person, so we make them Agents
      // This is a weird Sierra-specific hack and I don't think it
      // should exist, so I'm not going to put in all the effort to
      // extract this specific little bit of behaviour into a Sierra-specific
      // transformer
      case "00" if field.subfields.exists(_.tag == "t") => MarcAgent(field)
      case "00"                                         => MarcPerson(field)
      case "10" => MarcOrganisation(field)
      case "11" => MarcMeeting(field)
    }) match {
      case Failure(exception) =>
        // Log and ignore. A broken Agent contributor is not
        // worth throwing out the whole record
        error(ctx(exception.getMessage))
        None
      case Success(agent) =>
        Some(
          Contributor(
            agent = agent,
            roles = MarcContributionRoles(field).toList,
            primary = isPrimary(field.marcTag)
          )
        )
    }
  }
}
