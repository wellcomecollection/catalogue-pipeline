package uk.ac.wellcome.platform.idminter.database

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.platform.idminter.fixtures.IdentifiersDatabase

class TableProvisionerTest
    extends FunSpec
    with IdentifiersDatabase
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
