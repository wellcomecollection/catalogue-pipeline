package uk.ac.wellcome.platform.matcher.matcher

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.EitherValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scanamo.syntax._
import uk.ac.wellcome.models.matcher.{
  MatchedIdentifiers,
  MatcherResult,
  WorkIdentifier,
  WorkNode
}
import uk.ac.wellcome.models.work.generators.SierraWorkGenerators
import uk.ac.wellcome.models.work.internal.IdState.Identified
import uk.ac.wellcome.models.work.internal.MergeCandidate
import uk.ac.wellcome.platform.matcher.exceptions.MatcherException
import uk.ac.wellcome.platform.matcher.fixtures.MatcherFixtures
import uk.ac.wellcome.platform.matcher.models.{WorkGraph, WorkUpdate}
import uk.ac.wellcome.platform.matcher.storage.WorkGraphStore
import uk.ac.wellcome.storage.locking.dynamo.DynamoLockingService

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class WorkMatcherTest
    extends AnyFunSpec
    with Matchers
    with MatcherFixtures
    with ScalaFutures
    with MockitoSugar
    with SierraWorkGenerators
    with EitherValues {

  private val identifierA = Identified(
    createCanonicalId,
    createSierraSystemSourceIdentifierWith(value = "A"))
  private val identifierB = Identified(
    createCanonicalId,
    createSierraSystemSourceIdentifierWith(value = "B"))
  private val identifierC = Identified(
    createCanonicalId,
    createSierraSystemSourceIdentifierWith(value = "C"))

  it(
    "matches a work with no linked identifiers to itself only A and saves the updated graph A") {
    withLockTable { lockTable =>
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          withWorkMatcher(workGraphStore, lockTable) { workMatcher =>
            val work = identifiedWork()

            val sourceId = work.state.canonicalId
            val version = work.version

            whenReady(workMatcher.matchWork(work)) { matcherResult =>
              matcherResult shouldBe
                MatcherResult(Set(
                  MatchedIdentifiers(Set(WorkIdentifier(sourceId, version)))))

              val savedLinkedWork =
                get[WorkNode](dynamoClient, graphTable.name)('id -> sourceId)
                  .map(_.value)

              savedLinkedWork shouldBe Some(
                WorkNode(sourceId, version, Nil, ciHash(sourceId)))
            }
          }
        }
      }
    }
  }

  it("stores an invisible work and sends the work id") {
    withLockTable { lockTable =>
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          withWorkMatcher(workGraphStore, lockTable) { workMatcher =>
            val invisibleWork = identifiedWork().invisible()

            val sourceId = invisibleWork.state.canonicalId
            val version = invisibleWork.version

            whenReady(workMatcher.matchWork(invisibleWork)) { matcherResult =>
              matcherResult shouldBe
                MatcherResult(Set(
                  MatchedIdentifiers(Set(WorkIdentifier(sourceId, version)))))
              get[WorkNode](dynamoClient, graphTable.name)('id -> sourceId)
                .map(_.right.get) shouldBe Some(
                WorkNode(sourceId, version, Nil, ciHash(sourceId)))
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
            val work = identifiedWork(
              canonicalId = identifierA.canonicalId,
              sourceIdentifier = identifierA.sourceIdentifier)
              .withVersion(1)
              .mergeCandidates(List(MergeCandidate(identifierB)))

            whenReady(workMatcher.matchWork(work)) { identifiersList =>
              identifiersList shouldBe
                MatcherResult(
                  Set(MatchedIdentifiers(Set(
                    WorkIdentifier(identifierA.canonicalId, 1),
                    WorkIdentifier(identifierB.canonicalId, None)))))

              val savedWorkNodes = scan[WorkNode](dynamoClient, graphTable.name)
                .map(_.right.get)

              savedWorkNodes should contain theSameElementsAs List(
                WorkNode(
                  identifierA.canonicalId,
                  1,
                  List(identifierB.canonicalId),
                  ciHash(
                    List(identifierA.canonicalId, identifierB.canonicalId).sorted
                      .mkString("+"))),
                WorkNode(
                  identifierB.canonicalId,
                  None,
                  Nil,
                  ciHash(
                    List(identifierA.canonicalId, identifierB.canonicalId).sorted
                      .mkString("+")))
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
              identifierA.canonicalId,
              1,
              List(identifierB.canonicalId),
              ciHash(
                ciHash(
                  List(identifierA.canonicalId, identifierB.canonicalId).sorted
                    .mkString("+"))))
            val existingWorkB = WorkNode(
              identifierB.canonicalId,
              1,
              Nil,
              ciHash(
                ciHash(
                  List(identifierA.canonicalId, identifierB.canonicalId).sorted
                    .mkString("+"))))
            val existingWorkC = WorkNode(
              identifierC.canonicalId,
              1,
              Nil,
              ciHash(identifierC.canonicalId))
            put(dynamoClient, graphTable.name)(existingWorkA)
            put(dynamoClient, graphTable.name)(existingWorkB)
            put(dynamoClient, graphTable.name)(existingWorkC)

            val work =
              identifiedWork(
                canonicalId = identifierB.canonicalId,
                sourceIdentifier = identifierB.sourceIdentifier)
                .withVersion(2)
                .mergeCandidates(List(MergeCandidate(identifierC)))

            whenReady(workMatcher.matchWork(work)) { identifiersList =>
              identifiersList shouldBe
                MatcherResult(
                  Set(
                    MatchedIdentifiers(
                      Set(
                        WorkIdentifier(identifierA.canonicalId, 1),
                        WorkIdentifier(identifierB.canonicalId, 2),
                        WorkIdentifier(identifierC.canonicalId, 1)))))

              val savedNodes = scan[WorkNode](dynamoClient, graphTable.name)
                .map(_.right.get)

              savedNodes should contain theSameElementsAs List(
                WorkNode(
                  identifierA.canonicalId,
                  1,
                  List(identifierB.canonicalId),
                  ciHash(
                    List(
                      identifierA.canonicalId,
                      identifierB.canonicalId,
                      identifierC.canonicalId).sorted.mkString("+"))
                ),
                WorkNode(
                  identifierB.canonicalId,
                  2,
                  List(identifierC.canonicalId),
                  ciHash(
                    List(
                      identifierA.canonicalId,
                      identifierB.canonicalId,
                      identifierC.canonicalId).sorted.mkString("+"))
                ),
                WorkNode(
                  identifierC.canonicalId,
                  1,
                  Nil,
                  ciHash(
                    List(
                      identifierA.canonicalId,
                      identifierB.canonicalId,
                      identifierC.canonicalId).sorted.mkString("+")))
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
            val work = sierraIdentifiedWork()
            val workId = work.id
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
              val idA = identifierA.canonicalId
              val idB = identifierB.canonicalId
              val idC = identifierC.canonicalId
              workGraphStore.put(
                WorkGraph(
                  Set(
                    WorkNode(idA, 0, List(idB), componentId),
                    WorkNode(idB, 0, List(idC), componentId),
                    WorkNode(idC, 0, Nil, componentId),
                  )))

              val work = identifiedWork(
                canonicalId = identifierA.canonicalId,
                sourceIdentifier = identifierA.sourceIdentifier)
                .mergeCandidates(List(MergeCandidate(identifierB)))

              val failedLock = for {
                _ <- Future.successful(
                  lockDao.lock(componentId, UUID.randomUUID))
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

  it("fails if saving the updated links fails") {
    withLockTable { lockTable =>
      val mockWorkGraphStore = mock[WorkGraphStore]
      withWorkMatcher(mockWorkGraphStore, lockTable) { workMatcher =>
        val expectedException = new RuntimeException("Failed to put")
        when(mockWorkGraphStore.findAffectedWorks(any[WorkUpdate]))
          .thenReturn(Future.successful(WorkGraph(Set.empty)))
        when(mockWorkGraphStore.put(any[WorkGraph]))
          .thenThrow(expectedException)

        whenReady(workMatcher.matchWork(sierraIdentifiedWork()).failed) {
          actualException =>
            actualException shouldBe MatcherException(expectedException)
        }
      }
    }
  }
}
