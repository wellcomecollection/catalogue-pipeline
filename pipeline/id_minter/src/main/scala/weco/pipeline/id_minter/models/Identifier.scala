package weco.pipeline.id_minter.models

import scalikejdbc._
import weco.catalogue.internal_model.identifiers.{CanonicalId, SourceIdentifier}

/** Represents a set of identifiers as stored in MySQL */
case class Identifier(
  CanonicalId: CanonicalId,
  OntologyType: String = "Work",
  SourceSystem: String,
  SourceId: String
)

object Identifier {
  def apply(p: SyntaxProvider[Identifier])(rs: WrappedResultSet): Identifier =
    Identifier(
      CanonicalId = CanonicalId(rs.string(p.resultName.CanonicalId)),
      OntologyType = rs.string(p.resultName.OntologyType),
      SourceSystem = rs.string(p.resultName.SourceSystem),
      SourceId = rs.string(p.resultName.SourceId)
    )

  def apply(
    canonicalId: CanonicalId,
    sourceIdentifier: SourceIdentifier
  ): Identifier =
    Identifier(
      CanonicalId = canonicalId,
      OntologyType = sourceIdentifier.ontologyType,
      SourceSystem = sourceIdentifier.identifierType.id,
      SourceId = sourceIdentifier.value
    )
}
