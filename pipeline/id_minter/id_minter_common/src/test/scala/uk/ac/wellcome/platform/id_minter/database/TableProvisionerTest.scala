package uk.ac.wellcome.platform.id_minter.database

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.platform.id_minter.fixtures

case class FieldDescription(field: String,
                            dataType: String,
                            nullable: String,
                            key: String)

class TableProvisionerTest
    extends AnyFunSpec
    with fixtures.IdentifiersDatabase
    with Matchers {

  it("creates the Identifiers table") {
    withIdentifiersDatabase { identifiersTableConfig =>
      val databaseName = identifiersTableConfig.database
      val tableName = identifiersTableConfig.tableName

      new TableProvisioner(rdsClientConfig)
        .provision(databaseName, tableName)

      eventuallyTableExists(identifiersTableConfig)
    }
  }
}
