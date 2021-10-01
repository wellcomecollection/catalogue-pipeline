package weco.pipeline.matcher.matcher

import org.scalatest.EitherValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scanamo.syntax._
import weco.storage.locking.LockFailure
import weco.storage.locking.memory.{MemoryLockDao, MemoryLockingService}
import weco.fixtures.TimeAssertions
import weco.pipeline.matcher.fixtures.MatcherFixtures
import weco.pipeline.matcher.generators.WorkStubGenerators
import weco.pipeline.matcher.models.{MatchedIdentifiers, MatcherResult, WorkIdentifier, WorkNode, WorkStub}
import weco.pipeline.matcher.storage.{WorkGraphStore, WorkNodeDao}

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class WorkMatcherTest
    extends AnyFunSpec
    with Matchers
    with MatcherFixtures
    with ScalaFutures
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
                MatchedIdentifiers(Set(WorkIdentifier(work.id, work.version))))

            val savedLinkedWork =
              get[WorkNode](dynamoClient, graphTable.name)("id" === work.id)
                .map(_.right.value)

            savedLinkedWork shouldBe Some(
              WorkNode(
                id = work.id,
                version = work.version,
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
                    WorkIdentifier(identifierA.canonicalId, work.version),
                    WorkIdentifier(identifierB.canonicalId, None))))

            val savedWorkNodes = scan[WorkNode](dynamoClient, graphTable.name)
              .map(_.right.value)

            savedWorkNodes should contain theSameElementsAs List(
              WorkNode(
                id = identifierA.canonicalId,
                version = work.version,
                linkedIds = List(identifierB.canonicalId),
                componentId =
                  ciHash(identifierA.canonicalId, identifierB.canonicalId)
              ),
              WorkNode(
                id = identifierB.canonicalId,
                version = None,
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
            version = 1,
            linkedIds = List(identifierB.canonicalId),
            componentId =
              ciHash(identifierA.canonicalId, identifierB.canonicalId)
          )
          val existingWorkB = WorkNode(
            id = identifierB.canonicalId,
            version = 1,
            linkedIds = Nil,
            componentId =
              ciHash(identifierA.canonicalId, identifierB.canonicalId)
          )
          val existingWorkC = WorkNode(
            id = identifierC.canonicalId,
            version = 1,
            linkedIds = Nil,
            componentId = ciHash(identifierC.canonicalId)
          )
          put(dynamoClient, graphTable.name)(existingWorkA)
          put(dynamoClient, graphTable.name)(existingWorkB)
          put(dynamoClient, graphTable.name)(existingWorkC)

          val work = createWorkStubWith(
            id = identifierB,
            version = 2,
            referencedIds = Set(identifierC)
          )

          whenReady(workMatcher.matchWork(work)) { matcherResult =>
            assertRecent(matcherResult.createdTime)
            matcherResult.works shouldBe
              Set(
                MatchedIdentifiers(
                  Set(
                    WorkIdentifier(identifierA.canonicalId, 1),
                    WorkIdentifier(identifierB.canonicalId, 2),
                    WorkIdentifier(identifierC.canonicalId, 1))))

            val savedNodes = scan[WorkNode](dynamoClient, graphTable.name)
              .map(_.right.value)

            savedNodes should contain theSameElementsAs List(
              WorkNode(
                id = identifierA.canonicalId,
                version = 1,
                linkedIds = List(identifierB.canonicalId),
                componentId = ciHash(
                  identifierA.canonicalId,
                  identifierB.canonicalId,
                  identifierC.canonicalId)
              ),
              WorkNode(
                id = identifierB.canonicalId,
                version = 2,
                linkedIds = List(identifierC.canonicalId),
                componentId = ciHash(
                  identifierA.canonicalId,
                  identifierB.canonicalId,
                  identifierC.canonicalId)
              ),
              WorkNode(
                id = identifierC.canonicalId,
                version = 1,
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

  it("throws the locking error if it fails to lock primary works") {
    val expectedException = new Throwable("BOOM!")

    implicit val lockDao: MemoryLockDao[String, UUID] =
      new MemoryLockDao[String, UUID] {
        override def lock(id: String, contextId: UUID): LockResult =
          Left(LockFailure(id, e = expectedException))
      }

    val lockingService =
      new MemoryLockingService[MatcherResult, Future]()

    withWorkGraphTable { graphTable =>
      withWorkGraphStore(graphTable) { workGraphStore =>
        val work = createWorkStub

        val workMatcher = new WorkMatcher(workGraphStore, lockingService)

        val result = workMatcher.matchWork(work)

        whenReady(result.failed) {
          _.getMessage should startWith("FailedLock(")
        }
      }
    }
  }

  it("throws the locking error if it fails to lock secondary works") {
    val expectedException = new Throwable("BOOM!")

    withWorkGraphTable { graphTable =>
      withWorkGraphStore(graphTable) { workGraphStore =>
        val componentId = "ABC"
        val idA = identifierA.canonicalId
        val idB = identifierB.canonicalId
        val idC = identifierC.canonicalId

        val future = workGraphStore.put(
          Set(
            WorkNode(idA, version = 0, linkedIds = List(idB), componentId),
            WorkNode(idB, version = 0, linkedIds = List(idC), componentId),
            WorkNode(idC, version = 0, linkedIds = Nil, componentId),
          ))

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
                    Left(LockFailure(id, e = expectedException))
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
            _.getMessage should startWith("FailedLock(")
          }
        }
      }
    }
  }

  it("fails if saving the updated work fails") {
    val workNodeDao = new WorkNodeDao(
      dynamoClient = dynamoClient,
      dynamoConfig = createDynamoConfigWith(nonExistentTable)
    )

    val expectedException = new RuntimeException("Failed to put")

    val brokenStore = new WorkGraphStore(workNodeDao) {
      override def findAffectedWorks(w: WorkStub): Future[Set[WorkNode]] =
        Future.successful(Set[WorkNode]())

      override def put(nodes: Set[WorkNode]): Future[Unit] =
        Future.failed(expectedException)
    }

    withWorkMatcher(brokenStore) { workMatcher =>
      val work = createWorkStub

      whenReady(workMatcher.matchWork(work).failed) {
        _.getMessage shouldBe expectedException.getMessage
      }
    }
  }

  it("skips writing to the store if there are no changes") {
    withWorkGraphTable { graphTable =>
      withWorkNodeDao(graphTable) { workNodeDao =>
        var putCount = 0

        val spyStore = new WorkGraphStore(workNodeDao) {
          override def put(nodes: Set[WorkNode]): Future[Unit] = {
            putCount += 1
            super.put(nodes)
          }
        }

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
            putCount shouldBe 1
          }
        }
      }
    }
  }
}
