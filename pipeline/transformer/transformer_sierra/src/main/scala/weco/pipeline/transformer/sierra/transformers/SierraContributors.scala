package weco.pipeline.transformer.sierra.transformers

import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work._
import weco.pipeline.transformer.identifiers.LabelDerivedIdentifiers
import weco.pipeline.transformer.marc_common.logging.LoggingContext
import weco.pipeline.transformer.marc_common.transformers.MarcContributors
import weco.pipeline.transformer.sierra.data.SierraMarcDataConversions
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
    with LabelDerivedIdentifiers
    with SierraMarcDataConversions {

  type Output = List[Contributor[IdState.Unminted]]

  def apply(bibData: SierraBibData): List[Contributor[IdState.Unminted]] = {
    implicit val ctx: LoggingContext = LoggingContext("")
    MarcContributors(bibData).toList
  }
}
