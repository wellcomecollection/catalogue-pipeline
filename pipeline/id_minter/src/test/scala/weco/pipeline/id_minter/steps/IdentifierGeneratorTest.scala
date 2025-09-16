package weco.pipeline.id_minter.steps

import org.mockito.ArgumentMatchers._
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Inspectors, OptionValues}
import scalikejdbc._
import weco.fixtures.TestWith
import weco.catalogue.internal_model.generators.IdentifiersGenerators
import weco.catalogue.internal_model.identifiers.{
  IdentifierType,
  SourceIdentifier
}
import weco.pipeline.id_minter.config.models.IdentifiersTableConfig
import weco.pipeline.id_minter.database.IdentifiersDao
import weco.pipeline.id_minter.fixtures.IdentifiersDatabase
import weco.pipeline.id_minter.models.{Identifier, IdentifiersTable}
import org.scalatest.OneInstancePerTest
import org.scalatestplus.mockito.MockitoSugar

import scala.util.{Failure, Success, Try}

class IdentifierGeneratorTest
    extends AnyFunSpec
    with IdentifiersDatabase
    with Matchers
    with Inspectors
    with OptionValues
    with IdentifiersGenerators
    with MockitoSugar
    with OneInstancePerTest {

  def withIdentifierGenerator[R](
    maybeIdentifiersDao: Option[IdentifiersDao] = None,
    existingDaoEntries: Seq[Identifier] = Nil
  )(testWith: TestWith[(IdentifierGenerator, IdentifiersTable), R]): R =
    withIdentifiersDao(existingDaoEntries) {
      case (identifiersDao, table) =>
        val identifierGenerator = maybeIdentifiersDao match {
          case Some(dao) => new IdentifierGenerator(dao)
          case None      => new IdentifierGenerator(identifiersDao)
        }

        testWith((identifierGenerator, table))
    }

  it("queries the database and returns matching canonical IDs") {
    val sourceIdentifiers = (1 to 5).map(_ => createSourceIdentifier).toList
    val canonicalIds = (1 to 5).map(_ => createCanonicalId).toList
    val existingEntries = (sourceIdentifiers zip canonicalIds).map {
      case (sourceId, canonicalId) =>
        Identifier(
          canonicalId,
          sourceId
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
        implicit val session = NamedAutoSession('primary)

        val triedIds = identifierGenerator.retrieveOrGenerateCanonicalIds(
          sourceIdentifiers
        )

        triedIds shouldBe a[Success[_]]

        val ids = triedIds.get
        ids.size shouldBe sourceIdentifiers.length

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

  it("reconciles new identical identifiers") {
    // Given a batch with multiple identical source identifiers
    // Only generate an identifier for each unique source id.
    val sourceIdentifier1 = createSourceIdentifier
    val sourceIdentifier2 = createSourceIdentifier

    val sourceIdentifiers = List(
      sourceIdentifier1,
      sourceIdentifier2,
      sourceIdentifier1,
      sourceIdentifier2
    )

    withIdentifierGenerator() {
      case (identifierGenerator, identifiersTable) =>
        implicit val session = NamedAutoSession('primary)

        val triedIds = identifierGenerator.retrieveOrGenerateCanonicalIds(
          sourceIdentifiers
        )

        triedIds shouldBe a[Success[_]]

        val ids = triedIds.get
        ids.size shouldBe 2

        val i = identifiersTable.i
        val maybeIdentifiers = withSQL {
          select
            .from(identifiersTable as i)
            .where
            .in(i.SourceId, sourceIdentifiers.map(_.value))
        }.map(Identifier(i)).list.apply()

        maybeIdentifiers should have length 2
        maybeIdentifiers should contain theSameElementsAs
          List(sourceIdentifier1, sourceIdentifier2).flatMap(ids.get)
    }
  }

  it("tries looking up identifiers again if it fails to insert 'new' ids") {
    // Simulation of a race condition.  This test represents the behaviour of the
    // "second" participant in the race, with mocks representing the  "first"
    // participant.
    // When running in parallel (multiple threads, multiple hosts, whatever),
    // it is possible for a clash to occur, where two processes create a new
    // id for the same sourceIdentifier almost simultaneously.
    // The second process requests the id from the database, but it is not
    // yet there (because the first process has not yet stored it).
    // The first process finishes storing the id, then the second process
    // tries to store its new id, and fails.
    // In this scenario, the second process goes back to the database to
    // fetch the updated ids.
    val sourceIdentifiers = (1 to 5).map(_ => createSourceIdentifier).toList
    val canonicalIds = (1 to 5).map(_ => createCanonicalId).toList
    val existingEntries = (sourceIdentifiers zip canonicalIds).map {
      case (sourceId, canonicalId) =>
        (
          sourceId,
          Identifier(
            canonicalId,
            sourceId
          )
        )
    }.toMap

    val daoStub = mock[IdentifiersDao]("dao")

    // On the first call, the DAO returns an empty set of found identifiers,
    // and a full set of unminted identifiers, because the other participant
    // in the race has not yet saved its identifiers.
    // On the second call, they have been saved, so the full/empty states are switched.
    when(daoStub.lookupIds(any[List[SourceIdentifier]])(any[DBSession]))
      .thenReturn(
        Success(
          IdentifiersDao.LookupResult(
            existingIdentifiers = Map.empty,
            unmintedIdentifiers = sourceIdentifiers
          )
        )
      )
      .thenReturn(
        Success(
          IdentifiersDao.LookupResult(
            existingIdentifiers = existingEntries,
            unmintedIdentifiers = Nil
          )
        )
      )

    // When attempting to save, the first call fails, because the other participant
    // has now saved its identifiers.
    // The second run through will not call saveIdentifiers, as it will have retrieved
    // all of the ids from the database.
    when(daoStub.saveIdentifiers(any[List[Identifier]])(any[DBSession]))
      .thenReturn(
        Failure(IdentifiersDao.InsertError(Nil, new Exception("no."), Nil))
      )

    withIdentifierGenerator(Some(daoStub)) {
      case (identifierGenerator, _) =>
        val triedIds = identifierGenerator.retrieveOrGenerateCanonicalIds(
          sourceIdentifiers
        )

        triedIds shouldBe a[Success[_]]
        val ids = triedIds.get
        ids shouldBe existingEntries

        // LookupIds runs once at the start, then once again when saveIdentifiers fails.
        verify(daoStub, times(2)).lookupIds(sourceIdentifiers)
        // saveIdentifiers is only called first time, the second run through it has nothing to save.
        verify(daoStub, times(1)).saveIdentifiers(any[List[Identifier]])
    }
  }

  it("returns a failure if it fails registering new identifiers") {
    val config = IdentifiersTableConfig(
      database = createDatabaseName,
      tableName = createTableName
    )

    val saveException = new Exception("Don't do that please!")

    val identifiersDao = new IdentifiersDao(
      identifiers = new IdentifiersTable(config)
    ) {
      override def lookupIds(sourceIdentifiers: Seq[SourceIdentifier])(
        implicit session: DBSession
      ): Try[IdentifiersDao.LookupResult] =
        Success(
          IdentifiersDao.LookupResult(
            existingIdentifiers = Map.empty,
            unmintedIdentifiers = sourceIdentifiers.toList
          )
        )

      override def saveIdentifiers(ids: List[Identifier])(
        implicit session: DBSession
      ): Try[IdentifiersDao.InsertResult] =
        Failure(
          IdentifiersDao.InsertError(Nil, saveException, Nil)
        )
    }

    val sourceIdentifiers = (1 to 5).map(_ => createSourceIdentifier).toList

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
        implicit val session = NamedAutoSession('primary)

        val sourceIdentifier = createSourceIdentifierWith(
          ontologyType = "Item"
        )

        val triedId = identifierGenerator.retrieveOrGenerateCanonicalIds(
          List(sourceIdentifier)
        )

        val id = triedId.get.values.head.CanonicalId
        id.underlying should not be empty

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

  it("converts concept ontologyType values to Concept") {
    withIdentifierGenerator() {
      case (identifierGenerator, identifiersTable) =>
        implicit val session = NamedAutoSession('primary)

        val sourceIdentifier = createSourceIdentifierWith(
          identifierType = IdentifierType.LCNames,
          ontologyType = "Person"
        )

        val triedId = identifierGenerator.retrieveOrGenerateCanonicalIds(
          List(sourceIdentifier)
        )

        val id = triedId.get.values.head.CanonicalId
        id.underlying should not be empty

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
          sourceIdentifier = sourceIdentifier.copy(ontologyType = "Concept")
        )
    }
  }

  it("only mints one id for concepts with the same source identifier") {
    withIdentifierGenerator() {
      case (identifierGenerator, identifiersTable) =>
        implicit val session = NamedAutoSession('primary)

        val conceptSubTypes = List(
          "Person",
          "Organisation",
          "Place",
          "Agent",
          "Meeting",
          "Genre",
          "Period"
        )

        // Create the same source identifier for every possible concept type
        val sourceIdentifiers = conceptSubTypes.map(
          value =>
            createSourceIdentifierWith(
              identifierType = IdentifierType.LCNames,
              ontologyType = value,
              value = "123"
            )
        )

        val conceptSourceIdentifier = createSourceIdentifierWith(
          identifierType = IdentifierType.LCNames,
          ontologyType = "Concept",
          value = "123"
        )

        val triedId = identifierGenerator.retrieveOrGenerateCanonicalIds(
          sourceIdentifiers
        )

        val id = triedId.get.values.head.CanonicalId
        id.underlying should not be empty

        val i = identifiersTable.i

        // For each created identifier, check that the same single row is returned
        // and that its type is 'Concept'
        sourceIdentifiers.foreach(
          sourceIdentifier => {
            val maybeIdentifier = withSQL {

              select
                .from(identifiersTable as i)
                .where
                .eq(i.SourceId, sourceIdentifier.value)

            }.map(Identifier(i)).single.apply()

            maybeIdentifier shouldBe defined
            maybeIdentifier.get shouldBe Identifier(
              canonicalId = id,
              sourceIdentifier = conceptSourceIdentifier
            )
          }
        )
    }
  }
}
