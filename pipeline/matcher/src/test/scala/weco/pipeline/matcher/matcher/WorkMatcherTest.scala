package weco.pipeline.matcher.matcher

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{spy, times, verify, when}
import org.scalatest.EitherValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scanamo.syntax._
import weco.storage.locking.LockFailure
import weco.storage.locking.memory.{MemoryLockDao, MemoryLockingService}
import weco.fixtures.TimeAssertions
import weco.pipeline.matcher.exceptions.MatcherException
import weco.pipeline.matcher.fixtures.MatcherFixtures
import weco.pipeline.matcher.generators.WorkStubGenerators
import weco.pipeline.matcher.models.{
  MatchedIdentifiers,
  MatcherResult,
  WorkGraph,
  WorkIdentifier,
  WorkNode,
  WorkStub
}
import weco.pipeline.matcher.storage.WorkGraphStore

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class WorkMatcherTest
    extends AnyFunSpec
    with Matchers
    with MatcherFixtures
    with ScalaFutures
    with MockitoSugar
    with EitherValues
    with WorkStubGenerators
    with TimeAssertions {

  private val identifierA = createIdentifier("AAAAAAAA")
  private val identifierB = createIdentifier("BBBBBBBB")
  private val identifierC = createIdentifier("CCCCCCCC")

  it(
    "matches a work with no linked identifiers to itself only A and saves the updated graph A") {
    withWorkGraphTable { graphTable =>
      withWorkGraphStore(graphTable) { workGraphStore =>
        withWorkMatcher(workGraphStore) { workMatcher =>
          val work = createWorkStubWith(id = identifierA)

          whenReady(workMatcher.matchWork(work)) { matcherResult =>
            assertRecent(matcherResult.createdTime)
            matcherResult.works shouldBe
              Set(
                MatchedIdentifiers(identifiers = Set(WorkIdentifier(work)))
              )

            val savedLinkedWork =
              get[WorkNode](dynamoClient, graphTable.name)(
                "id" === work.id)
                .map(_.right.value)

            savedLinkedWork shouldBe Some(
              WorkNode(
                id = work.id,
                modifiedTime = work.modifiedTime,
                linkedIds = Nil,
                componentId = ciHash(work.id)
              )
            )
          }
        }
      }
    }
  }

  it(
    "matches a work with a single linked identifier A->B and saves the graph A->B") {
    withWorkGraphTable { graphTable =>
      withWorkGraphStore(graphTable) { workGraphStore =>
        withWorkMatcher(workGraphStore) { workMatcher =>
          val work = createWorkStubWith(
            id = identifierA,
            referencedIds = Set(identifierB)
          )

          whenReady(workMatcher.matchWork(work)) { matcherResult =>
            assertRecent(matcherResult.createdTime)
            matcherResult.works shouldBe
              Set(
                MatchedIdentifiers(
                  Set(
                    WorkIdentifier(identifierA.canonicalId, modifiedTime = work.modifiedTime),
                    WorkIdentifier(identifierB.canonicalId, modifiedTime = None))))

            val savedWorkNodes = scan[WorkNode](dynamoClient, graphTable.name)
              .map(_.right.value)

            savedWorkNodes should contain theSameElementsAs List(
              WorkNode(
                id = identifierA.canonicalId,
                modifiedTime = work.modifiedTime,
                linkedIds = List(identifierB.canonicalId),
                componentId =
                  ciHash(identifierA.canonicalId, identifierB.canonicalId)
              ),
              WorkNode(
                id = identifierB.canonicalId,
                modifiedTime = None,
                linkedIds = Nil,
                componentId =
                  ciHash(identifierA.canonicalId, identifierB.canonicalId)
              )
            )
          }
        }
      }
    }
  }

  it(
    "matches a previously stored work A->B with an update B->C and saves the graph A->B->C") {
    withWorkGraphTable { graphTable =>
      withWorkGraphStore(graphTable) { workGraphStore =>
        withWorkMatcher(workGraphStore) { workMatcher =>
          val existingWorkA = WorkNode(
            id = identifierA.canonicalId,
            modifiedTime = modifiedTime1,
            linkedIds = List(identifierB.canonicalId),
            componentId =
              ciHash(identifierA.canonicalId, identifierB.canonicalId)
          )
          val existingWorkB = WorkNode(
            id = identifierB.canonicalId,
            modifiedTime = modifiedTime1,
            linkedIds = Nil,
            componentId =
              ciHash(identifierA.canonicalId, identifierB.canonicalId)
          )
          val existingWorkC = WorkNode(
            id = identifierC.canonicalId,
            modifiedTime = modifiedTime1,
            linkedIds = Nil,
            componentId = ciHash(identifierC.canonicalId)
          )
          put(dynamoClient, graphTable.name)(existingWorkA)
          put(dynamoClient, graphTable.name)(existingWorkB)
          put(dynamoClient, graphTable.name)(existingWorkC)

          val work = createWorkStubWith(
            id = identifierB,
            modifiedTime = modifiedTime2,
            referencedIds = Set(identifierC)
          )

          whenReady(workMatcher.matchWork(work)) { matcherResult =>
            assertRecent(matcherResult.createdTime)
            matcherResult.works shouldBe
              Set(
                MatchedIdentifiers(
                  Set(
                    WorkIdentifier(identifierA.canonicalId, modifiedTime = modifiedTime1),
                    WorkIdentifier(identifierB.canonicalId, modifiedTime = modifiedTime2),
                    WorkIdentifier(identifierC.canonicalId, modifiedTime = modifiedTime1))))

            val savedNodes = scan[WorkNode](dynamoClient, graphTable.name)
              .map(_.right.value)

            savedNodes should contain theSameElementsAs List(
              WorkNode(
                id = identifierA.canonicalId,
                modifiedTime = modifiedTime1,
                linkedIds = List(identifierB.canonicalId),
                componentId = ciHash(
                  identifierA.canonicalId,
                  identifierB.canonicalId,
                  identifierC.canonicalId)
              ),
              WorkNode(
                id = identifierB.canonicalId,
                modifiedTime = modifiedTime2,
                linkedIds = List(identifierC.canonicalId),
                componentId = ciHash(
                  identifierA.canonicalId,
                  identifierB.canonicalId,
                  identifierC.canonicalId)
              ),
              WorkNode(
                id = identifierC.canonicalId,
                modifiedTime = modifiedTime1,
                linkedIds = Nil,
                componentId = ciHash(
                  identifierA.canonicalId,
                  identifierB.canonicalId,
                  identifierC.canonicalId))
            )
          }
        }
      }
    }
  }

  it("throws MatcherException if it fails to lock primary works") {
    implicit val lockDao: MemoryLockDao[String, UUID] =
      new MemoryLockDao[String, UUID] {
        override def lock(id: String, contextId: UUID): LockResult =
          Left(LockFailure(id, e = new Throwable("BOOM!")))
      }

    val lockingService =
      new MemoryLockingService[MatcherResult, Future]()

    withWorkGraphTable { graphTable =>
      withWorkGraphStore(graphTable) { workGraphStore =>
        val work = createWorkStub

        val workMatcher = new WorkMatcher(workGraphStore, lockingService)

        val result = workMatcher.matchWork(work)

        whenReady(result.failed) {
          _ shouldBe a[MatcherException]
        }
      }
    }
  }

  it("throws MatcherException if it fails to lock secondary works") {
    withWorkGraphTable { graphTable =>
      withWorkGraphStore(graphTable) { workGraphStore =>
        val componentId = "ABC"
        val idA = identifierA.canonicalId
        val idB = identifierB.canonicalId
        val idC = identifierC.canonicalId

        val future = workGraphStore.put(
          WorkGraph(Set(
            WorkNode(idA, modifiedTime = modifiedTime0, linkedIds = List(idB), componentId),
            WorkNode(idB, modifiedTime = modifiedTime0, linkedIds = List(idC), componentId),
            WorkNode(idC, modifiedTime = modifiedTime0, linkedIds = Nil, componentId),
          )))

        whenReady(future) { _ =>
          val work = createWorkStubWith(
            id = identifierA,
            referencedIds = Set(identifierB)
          )

          implicit val lockDao: MemoryLockDao[String, UUID] =
            new MemoryLockDao[String, UUID] {
              override def lock(id: String, contextId: UUID): LockResult =
                synchronized {
                  if (id == componentId) {
                    Left(LockFailure(id, e = new Throwable("BOOM!")))
                  } else {
                    super.lock(id, contextId)
                  }
                }
            }

          val lockingService =
            new MemoryLockingService[MatcherResult, Future]()

          val workMatcher = new WorkMatcher(workGraphStore, lockingService)

          val result = workMatcher.matchWork(work)

          whenReady(result.failed) {
            _ shouldBe a[MatcherException]
          }
        }
      }
    }
  }

  it("fails if saving the updated work fails") {
    val mockWorkGraphStore = mock[WorkGraphStore]
    withWorkMatcher(mockWorkGraphStore) { workMatcher =>
      val expectedException = new RuntimeException("Failed to put")
      when(mockWorkGraphStore.findAffectedWorks(any[WorkStub]))
        .thenReturn(Future.successful(WorkGraph(Set.empty)))
      when(mockWorkGraphStore.put(any[WorkGraph]))
        .thenThrow(expectedException)

      val work = createWorkStub

      whenReady(workMatcher.matchWork(work).failed) { actualException =>
        actualException shouldBe MatcherException(expectedException)
      }
    }
  }

  it("skips writing to the store if there are no changes") {
    withWorkGraphTable { graphTable =>
      withWorkGraphStore(graphTable) { workGraphStore =>
        val spyStore = spy(workGraphStore)

        val work = createWorkStub

        withWorkMatcher(spyStore) { workMatcher =>
          // Try to match the work more than once.  We have to match in sequence,
          // not in parallel, or the locking will block all but one of them from
          // doing anything non-trivial.
          val futures =
            workMatcher
              .matchWork(work)
              .flatMap { _ =>
                workMatcher.matchWork(work)
              }
              .flatMap { _ =>
                workMatcher.matchWork(work)
              }
              .flatMap { _ =>
                workMatcher.matchWork(work)
              }
              .flatMap { _ =>
                workMatcher.matchWork(work)
              }

          whenReady(futures) { _ =>
            verify(spyStore, times(1)).put(any[Set[WorkNode]])
          }
        }
      }
    }
  }
}
