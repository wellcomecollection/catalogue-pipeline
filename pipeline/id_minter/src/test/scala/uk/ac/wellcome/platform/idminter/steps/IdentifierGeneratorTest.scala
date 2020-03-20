package uk.ac.wellcome.platform.idminter.steps

import org.mockito.Matchers.anyListOf
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSpec, Inspectors, Matchers, OptionValues}
import scalikejdbc._
import uk.ac.wellcome.models.work.generators.IdentifiersGenerators
import uk.ac.wellcome.platform.idminter.database.{
  IdentifiersDao,
  TableProvisioner
}
import uk.ac.wellcome.platform.idminter.fixtures
import uk.ac.wellcome.platform.idminter.models.{Identifier, IdentifiersTable}
import uk.ac.wellcome.fixtures.TestWith

import scala.util.{Failure, Success}
import scala.collection.JavaConverters._

class IdentifierGeneratorTest
    extends FunSpec
    with fixtures.IdentifiersDatabase
    with Matchers
    with Inspectors
    with OptionValues
    with MockitoSugar
    with IdentifiersGenerators {

  def withIdentifierGenerator[R](maybeIdentifiersDao: Option[IdentifiersDao] =
                                   None,
                                 existingDaoEntries: Seq[Identifier] = Nil)(
    testWith: TestWith[(IdentifierGenerator, IdentifiersTable), R]) =
    withIdentifiersDatabase[R] { identifiersTableConfig =>
      val identifiersTable = new IdentifiersTable(identifiersTableConfig)

      new TableProvisioner(rdsClientConfig)
        .provision(
          database = identifiersTableConfig.database,
          tableName = identifiersTableConfig.tableName
        )

      val identifiersDao = maybeIdentifiersDao.getOrElse(
        new IdentifiersDao(DB.connect(), identifiersTable)
      )

      val identifierGenerator = new IdentifierGenerator(identifiersDao)
      eventuallyTableExists(identifiersTableConfig)

      if (existingDaoEntries.nonEmpty) {
        DB localTx { implicit session =>
          withSQL {
            insert
              .into(identifiersTable)
              .namedValues(
                identifiersTable.column.CanonicalId -> sqls.?,
                identifiersTable.column.OntologyType -> sqls.?,
                identifiersTable.column.SourceSystem -> sqls.?,
                identifiersTable.column.SourceId -> sqls.?
              )
          }.batch {
            existingDaoEntries.map { id =>
              Seq(id.CanonicalId, id.OntologyType, id.SourceSystem, id.SourceId)
            }: _*
          }.apply()
        }
      }

      testWith((identifierGenerator, identifiersTable))
    }

  it("queries the database and returns matching canonical IDs") {
    val sourceIdentifiers = (1 to 5).map(_ => createSourceIdentifier).toList
    val canonicalIds = (1 to 5).map(_ => createCanonicalId).toList
    val existingEntries = (sourceIdentifiers zip canonicalIds).map {
      case (sourceId, canonicalId) => Identifier(
        canonicalId, sourceId
      )
    }
    withIdentifierGenerator(existingDaoEntries = existingEntries) {
      case (identifierGenerator, _) =>
        val triedIds =
          identifierGenerator.retrieveOrGenerateCanonicalIds(sourceIdentifiers)

        triedIds shouldBe a[Success[_]]
        val idsMap = triedIds.get
        forAll(sourceIdentifiers zip canonicalIds) {
          case (sourceId, canonicalId) =>
            idsMap.get(sourceId).value.CanonicalId should be(canonicalId)
        }
    }
  }

  it("generates and saves new identifiers") {
    val sourceIdentifiers = (1 to 5).map(_ => createSourceIdentifier).toList

    withIdentifierGenerator() {
      case (identifierGenerator, identifiersTable) =>
        implicit val session = AutoSession

        val triedIds = identifierGenerator.retrieveOrGenerateCanonicalIds(
          sourceIdentifiers
        )

        triedIds shouldBe a[Success[_]]

        val ids = triedIds.get
        ids.size shouldBe (sourceIdentifiers.length)

        val i = identifiersTable.i
        val maybeIdentifiers = withSQL {
          select
            .from(identifiersTable as i)
            .where
            .in(i.SourceId, sourceIdentifiers.map(_.value))
        }.map(Identifier(i)).list.apply()

        maybeIdentifiers should have length sourceIdentifiers.length
        maybeIdentifiers should contain theSameElementsAs
          sourceIdentifiers.flatMap(ids.get)
    }
  }

  it("returns a failure if it fails registering new identifiers") {
    val identifiersDao = mock[IdentifiersDao]

    val sourceIdentifiers = (1 to 5).map(_ => createSourceIdentifier).toList

    val triedLookup = identifiersDao.lookupIds(sourceIdentifiers)

    when(triedLookup)
      .thenReturn(
        Success(IdentifiersDao.LookupResult(Map.empty, sourceIdentifiers)))

    val saveException = new Exception("Don't do that please!")
    when(
      identifiersDao.saveIdentifiers(
        anyListOf(classOf[Identifier]).asScala.toList))
      .thenThrow(IdentifiersDao.InsertError(Nil, saveException, Nil))

    withIdentifierGenerator(Some(identifiersDao)) {
      case (identifierGenerator, _) =>
        val triedGeneratingIds =
          identifierGenerator.retrieveOrGenerateCanonicalIds(
            sourceIdentifiers
          )

        triedGeneratingIds shouldBe a[Failure[_]]
        triedGeneratingIds.failed.get
          .asInstanceOf[IdentifiersDao.InsertError]
          .e shouldBe saveException
    }
  }

  it("preserves the ontologyType when generating new identifiers") {
    withIdentifierGenerator() {
      case (identifierGenerator, identifiersTable) =>
        implicit val session = AutoSession

        val sourceIdentifier = createSourceIdentifierWith(
          ontologyType = "Item"
        )

        val triedId = identifierGenerator.retrieveOrGenerateCanonicalIds(
          List(sourceIdentifier)
        )

        val id = triedId.get.values.head.CanonicalId
        id should not be (empty)

        val i = identifiersTable.i
        val maybeIdentifier = withSQL {

          select
            .from(identifiersTable as i)
            .where
            .eq(i.SourceId, sourceIdentifier.value)

        }.map(Identifier(i)).single.apply()

        maybeIdentifier shouldBe defined
        maybeIdentifier.get shouldBe Identifier(
          canonicalId = id,
          sourceIdentifier = sourceIdentifier
        )
    }
  }
}
