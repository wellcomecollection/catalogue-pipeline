package uk.ac.wellcome.platform.id_minter.models

import scalikejdbc._
import weco.catalogue.internal_model.identifiers.{CanonicalID, SourceIdentifier}

/** Represents a set of identifiers as stored in MySQL */
case class Identifier(
  CanonicalId: CanonicalID,
  OntologyType: String = "Work",
  SourceSystem: String,
  SourceId: String
)

object Identifier {
  def apply(p: SyntaxProvider[Identifier])(rs: WrappedResultSet): Identifier =
    Identifier(
      CanonicalId = CanonicalID(rs.string(p.resultName.CanonicalId)),
      OntologyType = rs.string(p.resultName.OntologyType),
      SourceSystem = rs.string(p.resultName.SourceSystem),
      SourceId = rs.string(p.resultName.SourceId)
    )

  def apply(canonicalId: CanonicalID,
            sourceIdentifier: SourceIdentifier): Identifier =
    Identifier(
      CanonicalId = canonicalId,
      OntologyType = sourceIdentifier.ontologyType,
      SourceSystem = sourceIdentifier.identifierType.id,
      SourceId = sourceIdentifier.value
    )
}
