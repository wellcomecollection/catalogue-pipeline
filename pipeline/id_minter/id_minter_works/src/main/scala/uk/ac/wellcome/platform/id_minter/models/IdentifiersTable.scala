package uk.ac.wellcome.platform.id_minter.models

import scalikejdbc._
import uk.ac.wellcome.platform.id_minter.config.models.IdentifiersTableConfig

class IdentifiersTable(identifiersTableConfig: IdentifiersTableConfig)
    extends SQLSyntaxSupport[Identifier] {
  override val schemaName = Some(identifiersTableConfig.database)
  override val tableName = identifiersTableConfig.tableName
  override val useSnakeCaseColumnName = false
  override val columns = Seq(
    "CanonicalId",
    "OntologyType",
    "SourceSystem",
    "SourceId"
  )

  val i = this.syntax("i")
}
