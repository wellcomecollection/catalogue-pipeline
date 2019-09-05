package uk.ac.wellcome.platform.matcher.matcher

import java.time.Instant
import java.util.UUID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSpec, Matchers}
import org.scanamo.syntax._

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
import uk.ac.wellcome.storage.locking.UnlockFailure
import uk.ac.wellcome.storage.locking.dynamo.{
  DynamoLockDao,
  DynamoLockingService,
  ExpiringLock
}

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
    withLockTable { lockTable =>
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          withWorkMatcher(workGraphStore, lockTable) { workMatcher =>
            val work = createUnidentifiedSierraWork
            val workId = work.sourceIdentifier.toString

            whenReady(workMatcher.matchWork(work)) { matcherResult =>
              matcherResult shouldBe
                MatcherResult(
                  Set(MatchedIdentifiers(Set(WorkIdentifier(workId, 1)))))

              val savedLinkedWork =
                get[WorkNode](dynamoClient, graphTable.name)('id -> workId)
                  .map(_.right.get)

              savedLinkedWork shouldBe Some(
                WorkNode(workId, 1, Nil, ciHash(workId)))
            }
          }
        }
      }
    }
  }

  it("doesn't store an invisible work and sends the work id") {
    withLockTable { lockTable =>
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          withWorkMatcher(workGraphStore, lockTable) { workMatcher =>
            val invisibleWork = createUnidentifiedInvisibleWork
            val workId = invisibleWork.sourceIdentifier.toString
            whenReady(workMatcher.matchWork(invisibleWork)) { matcherResult =>
              matcherResult shouldBe
                MatcherResult(
                  Set(MatchedIdentifiers(Set(WorkIdentifier(workId, 1)))))
              get[WorkNode](dynamoClient, graphTable.name)('id -> workId) shouldBe None
            }
          }
        }
      }
    }
  }

  it(
    "matches a work with a single linked identifier A->B and saves the graph A->B") {
    withLockTable { lockTable =>
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          withWorkMatcher(workGraphStore, lockTable) { workMatcher =>
            val work = createUnidentifiedWorkWith(
              sourceIdentifier = identifierA,
              mergeCandidates = List(MergeCandidate(identifierB))
            )
            whenReady(workMatcher.matchWork(work)) { identifiersList =>
              identifiersList shouldBe
                MatcherResult(
                  Set(MatchedIdentifiers(Set(
                    WorkIdentifier("sierra-system-number/A", 1),
                    WorkIdentifier("sierra-system-number/B", 0)))))

              val savedWorkNodes = scan[WorkNode](dynamoClient, graphTable.name)
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
    }
  }

  it(
    "matches a previously stored work A->B with an update B->C and saves the graph A->B->C") {
    withLockTable { lockTable =>
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          withWorkMatcher(workGraphStore, lockTable) { workMatcher =>
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
            put(dynamoClient, graphTable.name)(existingWorkA)
            put(dynamoClient, graphTable.name)(existingWorkB)
            put(dynamoClient, graphTable.name)(existingWorkC)

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

              val savedNodes = scan[WorkNode](dynamoClient, graphTable.name)
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
    }
  }

  it("throws MatcherException if it fails to lock primary works") {
    withLockTable { lockTable =>
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          withLockDao(dynamoClient, lockTable) { implicit lockDao =>
            val work = createUnidentifiedSierraWork
            val workId = work.sourceIdentifier.toString
            withWorkMatcherAndLockingService(
              workGraphStore,
              new DynamoLockingService) { workMatcher =>
              val failedLock = for {
                _ <- Future.successful(lockDao.lock(workId, UUID.randomUUID))
                result <- workMatcher.matchWork(work)
              } yield result
              whenReady(failedLock.failed) { failedMatch =>
                failedMatch shouldBe a[MatcherException]
              }

            }
          }
        }
      }
    }
  }

  it("throws MatcherException if it fails to lock secondary works") {
    withLockTable { lockTable =>
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          withLockDao(dynamoClient, lockTable) { implicit lockDao =>
            withWorkMatcherAndLockingService(
              workGraphStore,
              new DynamoLockingService) { workMatcher =>
              // A->B->C
              val componentId = "ABC"
              val idA = "sierra-system-number/A"
              val idB = "sierra-system-number/B"
              val idC = "sierra-system-number/C"
              workGraphStore.put(
                WorkGraph(
                  Set(
                    WorkNode(idA, 0, List(idB), componentId),
                    WorkNode(idB, 0, List(idC), componentId),
                    WorkNode(idC, 0, Nil, componentId),
                  )))
              val work = createUnidentifiedWorkWith(
                sourceIdentifier = identifierA,
                mergeCandidates = List(MergeCandidate(identifierB))
              )
              val failedLock = for {
                _ <- Future.successful(lockDao.lock(idC, UUID.randomUUID))
                result <- workMatcher.matchWork(work)
              } yield result
              whenReady(failedLock.failed) { failedMatch =>
                failedMatch shouldBe a[MatcherException]
              }
            }
          }
        }
      }
    }
  }

  // The new LockingService never passes on the information that an unlock has
  // been unsuccessful (whether as an exception or returned from a method).
  ignore("throws MatcherException if it fails to unlock") {
    withWorkGraphTable { graphTable =>
      withWorkGraphStore(graphTable) { workGraphStore =>
        implicit val lockDao = mock[DynamoLockDao]
        val error = new RuntimeException("i wont unlock")
        withWorkMatcherAndLockingService(
          workGraphStore,
          new DynamoLockingService) { workMatcher =>
          when(lockDao.lock(any[String], any[UUID]))
            .thenReturn(
              Right(
                ExpiringLock(
                  id = "id",
                  contextId = UUID.randomUUID,
                  created = Instant.now,
                  expires = Instant.now.plusSeconds(100))
              ))
          when(lockDao.unlock(any[UUID]))
            .thenReturn(Left(UnlockFailure(UUID.randomUUID, error)))
          whenReady(workMatcher.matchWork(createUnidentifiedSierraWork).failed) {
            failedMatch =>
              failedMatch shouldBe a[MatcherException]
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
            actualException shouldBe MatcherException(expectedException)
        }
      }
    }
  }
}
