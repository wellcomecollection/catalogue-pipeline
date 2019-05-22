package uk.ac.wellcome.platform.matcher.matcher

import java.util.UUID

import com.gu.scanamo.Scanamo
import com.gu.scanamo.syntax._
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.matcher.{MatchedIdentifiers, MatcherResult, WorkIdentifier, WorkNode}
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal.MergeCandidate
import uk.ac.wellcome.platform.matcher.exceptions.MatcherException
import uk.ac.wellcome.platform.matcher.fixtures.MatcherFixtures
import uk.ac.wellcome.platform.matcher.models.{WorkGraph, WorkUpdate}
import uk.ac.wellcome.platform.matcher.storage.WorkGraphStore
import uk.ac.wellcome.storage.UnlockFailure
import uk.ac.wellcome.storage.memory.MemoryLockDao

import scala.util.{Failure, Success}

class WorkMatcherTest
    extends FunSpec
    with Matchers
    with MatcherFixtures
    with MockitoSugar
    with WorksGenerators {

  private val identifierA = createSierraSystemSourceIdentifierWith(value = "A")
  private val identifierB = createSierraSystemSourceIdentifierWith(value = "B")
  private val identifierC = createSierraSystemSourceIdentifierWith(value = "C")

  it(
    "matches a work with no linked identifiers to itself only A and saves the updated graph A") {
    withWorkGraphTable { graphTable =>
      val workMatcher = createWorkMatcher(graphTable)

      val work = createUnidentifiedSierraWork
      val workId = work.sourceIdentifier.toString

      val result = workMatcher.matchWork(work)

      result shouldBe a[Success[_]]
      val matcherResult = result.get

      matcherResult shouldBe
        MatcherResult(
          Set(MatchedIdentifiers(Set(WorkIdentifier(workId, 1)))))

      val savedLinkedWork = Scanamo
        .get[WorkNode](dynamoDbClient)(graphTable.name)('id -> workId)
        .map(_.right.get)

      savedLinkedWork shouldBe Some(
        WorkNode(workId, 1, Nil, ciHash(workId)))
    }
  }

  it("doesn't store an invisible work and sends the work id") {
    withWorkGraphTable { graphTable =>
      val workMatcher = createWorkMatcher(graphTable)
      val invisibleWork = createUnidentifiedInvisibleWork
      val workId = invisibleWork.sourceIdentifier.toString

      val result = workMatcher.matchWork(invisibleWork)

      result shouldBe a[Success[_]]
      val matcherResult = result.get

      matcherResult shouldBe
        MatcherResult(
          Set(MatchedIdentifiers(Set(WorkIdentifier(workId, 1)))))

      Scanamo
        .get[WorkNode](dynamoDbClient)(graphTable.name)('id -> workId) shouldBe None
    }
  }

  it(
    "matches a work with a single linked identifier A->B and saves the graph A->B") {
    withWorkGraphTable { graphTable =>
      val workMatcher = createWorkMatcher(graphTable)
      val work = createUnidentifiedWorkWith(
        sourceIdentifier = identifierA,
        mergeCandidates = List(MergeCandidate(identifierB))
      )

      val result = workMatcher.matchWork(work)

      result shouldBe a[Success[_]]
      val identifiersList = result.get

      identifiersList shouldBe
        MatcherResult(
          Set(MatchedIdentifiers(Set(
            WorkIdentifier("sierra-system-number/A", 1),
            WorkIdentifier("sierra-system-number/B", 0)))))

      val savedWorkNodes = Scanamo
        .scan[WorkNode](dynamoDbClient)(graphTable.name)
        .map(_.right.get)

      savedWorkNodes should contain theSameElementsAs List(
        WorkNode(
          "sierra-system-number/A",
          1,
          List("sierra-system-number/B"),
          ciHash("sierra-system-number/A+sierra-system-number/B")),
        WorkNode(
          "sierra-system-number/B",
          0,
          Nil,
          ciHash("sierra-system-number/A+sierra-system-number/B"))
      )
    }
  }

  it(
    "matches a previously stored work A->B with an update B->C and saves the graph A->B->C") {
    withWorkGraphTable { graphTable =>
      val workMatcher = createWorkMatcher(graphTable)

      val existingWorkA = WorkNode(
        "sierra-system-number/A",
        1,
        List("sierra-system-number/B"),
        "sierra-system-number/A+sierra-system-number/B")
      val existingWorkB = WorkNode(
        "sierra-system-number/B",
        1,
        Nil,
        "sierra-system-number/A+sierra-system-number/B")
      val existingWorkC = WorkNode(
        "sierra-system-number/C",
        1,
        Nil,
        "sierra-system-number/C")
      Scanamo.put(dynamoDbClient)(graphTable.name)(existingWorkA)
      Scanamo.put(dynamoDbClient)(graphTable.name)(existingWorkB)
      Scanamo.put(dynamoDbClient)(graphTable.name)(existingWorkC)

      val work = createUnidentifiedWorkWith(
        sourceIdentifier = identifierB,
        version = 2,
        mergeCandidates = List(MergeCandidate(identifierC)))

      val result = workMatcher.matchWork(work)

      result shouldBe a[Success[_]]
      val identifiersList = result.get

      identifiersList shouldBe
        MatcherResult(
          Set(
            MatchedIdentifiers(
              Set(
                WorkIdentifier("sierra-system-number/A", 1),
                WorkIdentifier("sierra-system-number/B", 2),
                WorkIdentifier("sierra-system-number/C", 1)))))

      val savedNodes = Scanamo
        .scan[WorkNode](dynamoDbClient)(graphTable.name)
        .map(_.right.get)

      savedNodes should contain theSameElementsAs List(
        WorkNode(
          "sierra-system-number/A",
          1,
          List("sierra-system-number/B"),
          ciHash(
            "sierra-system-number/A+sierra-system-number/B+sierra-system-number/C")),
        WorkNode(
          "sierra-system-number/B",
          2,
          List("sierra-system-number/C"),
          ciHash(
            "sierra-system-number/A+sierra-system-number/B+sierra-system-number/C")),
        WorkNode(
          "sierra-system-number/C",
          1,
          Nil,
          ciHash(
            "sierra-system-number/A+sierra-system-number/B+sierra-system-number/C"))
      )
    }
  }

  it("throws MatcherException if it fails to lock primary works") {
    val lockDao = createLockDao
    val lockingService = createLockingService(lockDao)

    withWorkGraphTable { graphTable =>
      val workMatcher = createWorkMatcher(graphTable, lockingService)

      val work = createUnidentifiedSierraWork
      val workId = work.sourceIdentifier.toString

      lockDao.lock(workId, contextId = UUID.randomUUID())
      val result = workMatcher.matchWork(work)

      result shouldBe a[Failure[_]]
      result.failed.get shouldBe a[MatcherException]
    }
  }

  it("throws MatcherException if it fails to lock secondary works") {
    val lockDao = createLockDao
    val lockingService = createLockingService(lockDao)

    withWorkGraphTable { graphTable =>
      withWorkGraphStore(graphTable) { workGraphStore =>
        val workMatcher = new WorkMatcher(
          workGraphStore = workGraphStore,
          lockingService = lockingService
        )

        // A->B->C
        workGraphStore.put(WorkGraph(Set(
          WorkNode(
            "sierra-system-number/A",
            0,
            List("sierra-system-number/B"),
            "sierra-system-number/A+sierra-system-number/B+sierra-system-number/C"),
          WorkNode(
            "sierra-system-number/B",
            0,
            List("sierra-system-number/C"),
            "sierra-system-number/A+sierra-system-number/B+sierra-system-number/C"),
          WorkNode("sierra-system-number/C", 0, Nil, "sierra-system-number/A+sierra-system-number/B+sierra-system-number/C")
        )))

        val work = createUnidentifiedWorkWith(
          sourceIdentifier = identifierA,
          mergeCandidates = List(MergeCandidate(identifierB))
        )

        lockDao.lock("sierra-system-number/C", contextId = UUID.randomUUID())
        val result = workMatcher.matchWork(work)

        result shouldBe a[Failure[_]]
        result.failed.get shouldBe a[MatcherException]
      }
    }
  }

  it("throws MatcherException if it fails to unlock") {
    val brokenLockDao: MatcherLockDao = new MemoryLockDao[String, UUID] {
      override def unlock(contextId: UUID): UnlockResult = Left(UnlockFailure(contextId, new Throwable("BOOM!")))
    }

    val lockingService = createLockingService(brokenLockDao)

    withWorkGraphTable { graphTable =>
      val workMatcher = createWorkMatcher(graphTable, lockingService)

      val result = workMatcher.matchWork(createUnidentifiedSierraWork)
      result shouldBe a[Failure[_]]
      result.failed.get shouldBe a[MatcherException]
    }
  }

  it("fails if saving the updated links fails") {
    val mockWorkGraphStore = mock[WorkGraphStore]

    val workMatcher = new WorkMatcher(
      workGraphStore = mockWorkGraphStore,
      lockingService = createLockingService()
    )

    val expectedException = new RuntimeException("Failed to put")
    when(mockWorkGraphStore.findAffectedWorks(any[WorkUpdate]))
      .thenReturn(Success(WorkGraph(Set.empty)))
    when(mockWorkGraphStore.put(any[WorkGraph]))
      .thenThrow(expectedException)

    val result = workMatcher.matchWork(createUnidentifiedSierraWork)
    result shouldBe a[Failure[_]]
    result.failed.get shouldBe expectedException
  }
}
