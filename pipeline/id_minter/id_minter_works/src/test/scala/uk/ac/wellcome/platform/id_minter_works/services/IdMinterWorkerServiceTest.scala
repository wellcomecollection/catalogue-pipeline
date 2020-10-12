package uk.ac.wellcome.platform.id_minter_works.services

import org.scalatest.funspec.AnyFunSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import scalikejdbc._

import uk.ac.wellcome.platform.id_minter.database.{
  FieldDescription,
  IdentifiersDao
}
import uk.ac.wellcome.platform.id_minter_works.fixtures.WorkerServiceFixture

class IdMinterWorkerServiceTest
    extends AnyFunSpec
    with Matchers
    with MockitoSugar
    with WorkerServiceFixture {

  it("creates the Identifiers table in MySQL upon startup") {
    withIdentifiersDatabase { identifiersTableConfig =>
      val identifiersDao = mock[IdentifiersDao]
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
