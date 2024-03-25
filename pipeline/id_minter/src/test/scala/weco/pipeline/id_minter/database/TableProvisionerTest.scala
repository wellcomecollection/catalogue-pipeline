package weco.pipeline.id_minter.database

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.pipeline.id_minter.fixtures.IdentifiersDatabase

case class FieldDescription(
  field: String,
  dataType: String,
  nullable: String,
  key: String
)

class TableProvisionerTest
    extends AnyFunSpec
    with IdentifiersDatabase
    with Matchers {

  it("creates the Identifiers table") {
    withIdentifiersTable {
      identifiersTableConfig =>
        val databaseName = identifiersTableConfig.database
        val tableName = identifiersTableConfig.tableName

        new TableProvisioner(rdsClientConfig)
          .provision(databaseName, tableName)

        eventuallyTableExists(identifiersTableConfig)
    }
  }
}
