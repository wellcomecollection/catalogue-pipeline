package weco.pipeline.id_minter.steps

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.identifiers.SourceIdentifier
import weco.pipeline.id_minter.database.IdentifiersDao
import weco.pipeline.id_minter.models.Identifier
import weco.pipeline.id_minter.utils.Identifiable

import scala.util.{Failure, Success, Try}

trait ConceptsSourceIdentifierAdjuster {
  // This list should be kept in sync with the one defined in `catalogue_graph/src/models/graph_node.py`
  private val conceptSubTypes = List(
    "Person",
    "Organisation",
    "Place",
    "Agent",
    "Meeting",
    "Genre",
    "Period"
  )

  def adjustSourceIdentifier(identifier: SourceIdentifier): SourceIdentifier = {
    // When minting ids for concepts, we don't care about ontology types. For example, an 'Agent' with a given
    // Library of Congress source identifier should have the same id as a 'Person' with the same source identifier.
    if (conceptSubTypes.contains(identifier.ontologyType))
      identifier.copy(ontologyType = "Concept")
    else
      identifier
  }
}

trait CanonicalIdentifierGenerator {
  def retrieveOrGenerateCanonicalIds(
    sourceIdentifiers: Seq[SourceIdentifier]
  ): Try[Map[SourceIdentifier, Identifier]]
}

class IdentifierGenerator(identifiersDao: IdentifiersDao)
    extends CanonicalIdentifierGenerator
      with ConceptsSourceIdentifierAdjuster
    with Logging {
  import IdentifiersDao._


  /*
   * Fetch canonicalIds for any existing sourceIdentifiers, generate
   * canonicalIds for any new ones and save them.  Retrying if
   * the save fails.
   *
   * In any kind of parallel execution, a race condition may occur here,
   * whereby two executors try to create new identifiers for the same
   * SourceIdentifier simultaneously.  In that situation, the "second"
   * executor will fail with an InsertError due to the uniqueness constraint.
   *
   * When that happens, this function will make one retry attempt, refreshing
   * its list of pre-existing identifiers.  This *should* be sufficient, as it
   * would be terribly bad luck if this document has new identifiers in it
   * and those new identifiers are also spread across multiple other documents
   * that are being processed at the same time, and in between the first and
   * the second calls to lookupIds, the second "other" document finishes writing
   * its ids.
   *
   */
  def retrieveOrGenerateCanonicalIds(
    sourceIdentifiers: Seq[SourceIdentifier]
  ): Try[Map[SourceIdentifier, Identifier]] =
    retrieveOrGenerateCanonicalIdsOnce(sourceIdentifiers) match {
      case Failure(_: InsertError) =>
        retrieveOrGenerateCanonicalIdsOnce(sourceIdentifiers)
      case success => success
    }

  /*
   * This function fetches canonicalIds for sourceIdentifiers, and generates
   * and saves canonicalIds where it can't find existing ones.
   *
   * Be aware that this means that if it is called in a multi-threaded
   * environment there may be inconsistency between the threads and some of
   * the updates will fail due to duplicate key errors, when an identifier
   * is saved by another thread after `lookupIds` has been called.
   */
  private def retrieveOrGenerateCanonicalIdsOnce(
    sourceIdentifiers: Seq[SourceIdentifier]
  ): Try[Map[SourceIdentifier, Identifier]] = {

    val adjustedSourceIdentifiers = sourceIdentifiers.map {
      identifier => adjustSourceIdentifier(identifier)
    }

    identifiersDao
      .lookupIds(adjustedSourceIdentifiers)
      .flatMap {
        case LookupResult(existingIdentifiersMap, unmintedIdentifiers) =>
          generateAndSaveCanonicalIds(unmintedIdentifiers).map {
            newIdentifiers =>
              val newIdentifiersMap: Map[SourceIdentifier, Identifier] =
                (unmintedIdentifiers zip newIdentifiers).toMap
              existingIdentifiersMap ++ newIdentifiersMap
          }
      }
  }

  private def generateAndSaveCanonicalIds(
    unmintedIdentifiers: List[SourceIdentifier]
  ): Try[List[Identifier]] =
    unmintedIdentifiers match {
      case Nil => Success(Nil)
      case _ =>
        identifiersDao
          .saveIdentifiers(
            unmintedIdentifiers.map {
              id =>
                Identifier(
                  canonicalId = Identifiable.generate,
                  sourceIdentifier = id
                )
            }
          )
          .map(_.succeeded)
    }

}
