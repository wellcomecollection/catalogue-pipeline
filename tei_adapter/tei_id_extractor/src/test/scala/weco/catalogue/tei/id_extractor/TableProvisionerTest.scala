package weco.catalogue.tei.id_extractor

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

case class FieldDescription(field: String,
                            dataType: String,
                            nullable: String,
                            key: String)

class TableProvisionerTest
    extends AnyFunSpec
    with fixtures.PathIdDatabase
    with Matchers {

  it("creates the PathId table") {
    withPathIdTable { pathIdTableConfig =>
      val databaseName = pathIdTableConfig.database
      val tableName = pathIdTableConfig.tableName

      new TableProvisioner(rdsClientConfig)
        .provision(databaseName, tableName)

      eventuallyTableExists(pathIdTableConfig)
    }
  }
}
