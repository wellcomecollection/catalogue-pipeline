package weco.catalogue.tei.id_extractor.database

import scalikejdbc._
import weco.catalogue.tei.id_extractor.models.PathId

class PathIdTable(pathIdTableConfig: PathIdTableConfig) extends SQLSyntaxSupport[PathId] {
  override val schemaName = Some(pathIdTableConfig.database)
  override val tableName = pathIdTableConfig.tableName
  override val useSnakeCaseColumnName = false
  override val columns = Seq(
    "path",
    "id",
    "timeModified"
  )

  val p = this.syntax("p")
}
