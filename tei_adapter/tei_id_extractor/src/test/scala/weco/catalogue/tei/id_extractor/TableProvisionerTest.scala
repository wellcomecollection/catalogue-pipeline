package weco.catalogue.tei.id_extractor

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.tei.id_extractor.database.TableProvisioner

case class FieldDescription(
  field: String,
  dataType: String,
  nullable: String,
  key: String
)

class TableProvisionerTest
    extends AnyFunSpec
    with fixtures.PathIdDatabase
    with Matchers {

  it("creates the PathId table") {
    withPathIdDatabase {
      pathIdTableConfig =>
        new TableProvisioner(rdsClientConfig, pathIdTableConfig)
          .provision()

        eventuallyTableExists(pathIdTableConfig)
    }
  }
}
