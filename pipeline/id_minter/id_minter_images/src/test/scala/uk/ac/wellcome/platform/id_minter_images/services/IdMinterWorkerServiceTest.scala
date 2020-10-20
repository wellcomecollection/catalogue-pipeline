package uk.ac.wellcome.platform.id_minter_images.services

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scalikejdbc._
import uk.ac.wellcome.platform.id_minter.database.{
  FieldDescription,
  IdentifiersDao
}
import uk.ac.wellcome.platform.id_minter.models.IdentifiersTable
import uk.ac.wellcome.platform.id_minter_images.fixtures.WorkerServiceFixture

class IdMinterWorkerServiceTest
    extends AnyFunSpec
    with Matchers
    with WorkerServiceFixture {

  it("creates the Identifiers table in MySQL upon startup") {
    withIdentifiersTable { identifiersTableConfig =>
      val identifiersDao = new IdentifiersDao(
        identifiers = new IdentifiersTable(identifiersTableConfig)
      )

      withWorkerService(
        identifiersDao = identifiersDao,
        identifiersTableConfig = identifiersTableConfig) { _ =>
        val database: SQLSyntax =
          SQLSyntax.createUnsafely(identifiersTableConfig.database)
        val table: SQLSyntax =
          SQLSyntax.createUnsafely(identifiersTableConfig.tableName)

        eventually {
          val fields = NamedDB('primary) readOnly { implicit session =>
            sql"DESCRIBE $database.$table"
              .map(
                rs =>
                  FieldDescription(
                    rs.string("Field"),
                    rs.string("Type"),
                    rs.string("Null"),
                    rs.string("Key")))
              .list()
              .apply()
          }

          fields should not be empty
        }
      }
    }
  }
}
