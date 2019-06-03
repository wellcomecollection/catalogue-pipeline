package uk.ac.wellcome.platform.matcher.matcher

import java.time.Instant
import java.util.UUID

import com.gu.scanamo.Scanamo
import com.gu.scanamo.syntax._
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.matcher.{
  MatchedIdentifiers,
  MatcherResult,
  WorkIdentifier,
  WorkNode
}
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal.MergeCandidate
import uk.ac.wellcome.platform.matcher.exceptions.MatcherException
import uk.ac.wellcome.platform.matcher.fixtures.MatcherFixtures
import uk.ac.wellcome.platform.matcher.models.{WorkGraph, WorkUpdate}
import uk.ac.wellcome.platform.matcher.storage.WorkGraphStore
import uk.ac.wellcome.storage.locking.{
  DynamoRowLockDao,
  FailedUnlockException,
  Identifier,
  RowLock
}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class WorkMatcherTest
    extends FunSpec
    with Matchers
    with MatcherFixtures
    with ScalaFutures
    with MockitoSugar
    with WorksGenerators {

  private val identifierA = createSierraSystemSourceIdentifierWith(value = "A")
  private val identifierB = createSierraSystemSourceIdentifierWith(value = "B")
  private val identifierC = createSierraSystemSourceIdentifierWith(value = "C")

  it(
    "matches a work with no linked identifiers to itself only A and saves the updated graph A") {
    val lockingService = createLockingService()

    withWorkGraphTable { graphTable =>
      withWorkGraphStore(graphTable) { workGraphStore =>
        val workMatcher = new WorkMatcher(workGraphStore, lockingService)

        val work = createUnidentifiedSierraWork
        val workId = work.sourceIdentifier.toString

        whenReady(workMatcher.matchWork(work)) { matcherResult =>
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
    }
  }

  it("doesn't store an invisible work and sends the work id") {
    val lockingService = createLockingService()

    withWorkGraphTable { graphTable =>
      withWorkGraphStore(graphTable) { workGraphStore =>
        val workMatcher = new WorkMatcher(workGraphStore, lockingService)

        val invisibleWork = createUnidentifiedInvisibleWork
        val workId = invisibleWork.sourceIdentifier.toString
        whenReady(workMatcher.matchWork(invisibleWork)) { matcherResult =>
          matcherResult shouldBe
            MatcherResult(
              Set(MatchedIdentifiers(Set(WorkIdentifier(workId, 1)))))

          Scanamo
            .get[WorkNode](dynamoDbClient)(graphTable.name)('id -> workId) shouldBe None
        }
      }
    }
  }

  it(
    "matches a work with a single linked identifier A->B and saves the graph A->B") {
    val lockingService = createLockingService()

    withWorkGraphTable { graphTable =>
      withWorkGraphStore(graphTable) { workGraphStore =>
        val workMatcher = new WorkMatcher(workGraphStore, lockingService)

        val work = createUnidentifiedWorkWith(
          sourceIdentifier = identifierA,
          mergeCandidates = List(MergeCandidate(identifierB))
        )
        whenReady(workMatcher.matchWork(work)) { identifiersList =>
          identifiersList shouldBe
            MatcherResult(
              Set(
                MatchedIdentifiers(Set(
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
    }
  }

  it(
    "matches a previously stored work A->B with an update B->C and saves the graph A->B->C") {
    val lockingService = createLockingService()

    withWorkGraphTable { graphTable =>
      withWorkGraphStore(graphTable) { workGraphStore =>
        val workMatcher = new WorkMatcher(workGraphStore, lockingService)

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
        val existingWorkC =
          WorkNode("sierra-system-number/C", 1, Nil, "sierra-system-number/C")
        Scanamo.put(dynamoDbClient)(graphTable.name)(existingWorkA)
        Scanamo.put(dynamoDbClient)(graphTable.name)(existingWorkB)
        Scanamo.put(dynamoDbClient)(graphTable.name)(existingWorkC)

        val work = createUnidentifiedWorkWith(
          sourceIdentifier = identifierB,
          version = 2,
          mergeCandidates = List(MergeCandidate(identifierC)))

        whenReady(workMatcher.matchWork(work)) { identifiersList =>
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
    }
  }

  it("throws MatcherException if it fails to lock primary works") {
    val lockDao = createLockDao
    val lockingService = createLockingService(lockDao)

    withWorkGraphTable { graphTable =>
      withWorkGraphStore(graphTable) { workGraphStore =>
        val workMatcher = new WorkMatcher(workGraphStore, lockingService)

        val work = createUnidentifiedSierraWork
        val workId = work.sourceIdentifier.toString

        lockDao.lock(workId, contextId = UUID.randomUUID) shouldBe a[Right[_,
                                                                           _]]

        whenReady(workMatcher.matchWork(work).failed) { failedMatch =>
          failedMatch shouldBe a[MatcherException]
        }
      }
    }
  }

  it("throws MatcherException if it fails to lock secondary works") {
    val lockDao = createLockDao
    val lockingService = createLockingService(lockDao)

    withWorkGraphTable { graphTable =>
      withWorkGraphStore(graphTable) { workGraphStore =>
        val workMatcher = new WorkMatcher(workGraphStore, lockingService)

        // A->B->C
        workGraphStore.put(
          WorkGraph(Set(
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
            WorkNode(
              "sierra-system-number/C",
              0,
              Nil,
              "sierra-system-number/A+sierra-system-number/B+sierra-system-number/C")
          )))

        val work = createUnidentifiedWorkWith(
          sourceIdentifier = identifierA,
          mergeCandidates = List(MergeCandidate(identifierB))
        )

        lockDao.lock("sierra-system-number/C", contextId = UUID.randomUUID) shouldBe a[
          Right[_, _]]

        whenReady(workMatcher.matchWork(work).failed) { failedMatch =>
          failedMatch shouldBe a[MatcherException]
        }
      }
    }
  }

  it("throws MatcherException if it fails to unlock") {
    withMockMetricsSender { mockMetricsSender =>
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          val mockDynamoRowLockDao = mock[DynamoRowLockDao]
          withLockingService(mockDynamoRowLockDao, mockMetricsSender) {
            dynamoLockingService =>
              withWorkMatcherAndLockingService(
                workGraphStore,
                dynamoLockingService) { workMatcher =>
                when(mockDynamoRowLockDao.lockRow(any[Identifier], any[String]))
                  .thenReturn(
                    Future.successful(
                      RowLock(
                        id = "id",
                        contextId = "contextId",
                        created = Instant.now,
                        expires = Instant.now.plusSeconds(100))
                    ))

                when(mockDynamoRowLockDao.unlockRows(any[String])).thenReturn(
                  Future.failed(
                    FailedUnlockException("test", new RuntimeException)))

                whenReady(
                  workMatcher
                    .matchWork(createUnidentifiedSierraWork)
                    .failed) { failedMatch =>
                  failedMatch shouldBe a[MatcherException]
                }
              }
          }
        }
      }
    }
  }

  it("fails if saving the updated links fails") {
    withLockTable { lockTable =>
      val mockWorkGraphStore = mock[WorkGraphStore]
      withWorkMatcher(mockWorkGraphStore, lockTable) { workMatcher =>
        val expectedException = new RuntimeException("Failed to put")
        when(mockWorkGraphStore.findAffectedWorks(any[WorkUpdate]))
          .thenReturn(Future.successful(WorkGraph(Set.empty)))
        when(mockWorkGraphStore.put(any[WorkGraph]))
          .thenThrow(expectedException)

        whenReady(workMatcher.matchWork(createUnidentifiedSierraWork).failed) {
          actualException =>
            actualException shouldBe expectedException
        }
      }
    }
  }
}
