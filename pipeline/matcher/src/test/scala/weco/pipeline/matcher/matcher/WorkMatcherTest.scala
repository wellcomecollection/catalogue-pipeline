package weco.pipeline.matcher.matcher

import org.scalatest.EitherValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scanamo.generic.auto._
import weco.catalogue.internal_model.identifiers.CanonicalId
import weco.fixtures.TimeAssertions
import weco.pipeline.matcher.fixtures.MatcherFixtures
import weco.pipeline.matcher.generators.WorkNodeGenerators
import weco.pipeline.matcher.models._
import weco.pipeline.matcher.storage.{WorkGraphStore, WorkNodeDao}
import weco.storage.fixtures.DynamoFixtures.Table
import weco.storage.locking.LockFailure
import weco.storage.locking.memory.{MemoryLockDao, MemoryLockingService}

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.higherKinds
import scala.util.{Failure, Success, Try}

class WorkMatcherTest
    extends AnyFunSpec
    with Matchers
    with MatcherFixtures
    with ScalaFutures
    with EitherValues
    with WorkNodeGenerators
    with TimeAssertions {

  it(
    "matches a work with no linked identifiers to itself only A and saves the updated graph A") {
    withWorkGraphTable { graphTable =>
      withWorkGraphStore(graphTable) { workGraphStore =>
        withWorkMatcher(workGraphStore) { workMatcher =>
          val work = createWorkWith(id = idA)

          whenReady(workMatcher.matchWork(work)) { matcherResult =>
            assertRecent(matcherResult.createdTime)
            matcherResult.works shouldBe
              Set(
                MatchedIdentifiers(Set(WorkIdentifier(work.id, work.version))))

            val savedLinkedWork = getWorkNode(work.id, graphTable)

            savedLinkedWork shouldBe
              WorkNode(
                id = work.id,
                subgraphId = SubgraphId(work.id),
                componentIds = List(work.id),
                sourceWork = SourceWorkData(
                  id = work.state.sourceIdentifier,
                  version = work.version),
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
          val work = createWorkWith(
            id = idA,
            mergeCandidateIds = Set(idB)
          )

          whenReady(workMatcher.matchWork(work)) { matcherResult =>
            assertRecent(matcherResult.createdTime)
            matcherResult.works shouldBe
              Set(MatchedIdentifiers(Set(WorkIdentifier(idA, work.version))))

            val savedWorkNodes = scanTable[WorkNode](graphTable)
              .map(_.right.value)

            savedWorkNodes should contain theSameElementsAs List(
              WorkNode(
                id = idA,
                subgraphId = SubgraphId(idA, idB),
                componentIds = List(idA, idB),
                sourceWork = SourceWorkData(
                  id = work.state.sourceIdentifier,
                  version = work.version,
                  mergeCandidateIds = List(idB)
                ),
              ),
              WorkNode(
                id = idB,
                subgraphId = SubgraphId(idA, idB),
                componentIds = List(idA, idB),
              ),
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
          val (workA, workB) = createTwoWorks("A->B")
          val workC = createOneWork("C")

          putTableItems(
            items = Seq(workA, workB, workC),
            table = graphTable
          )

          val work = createWorkWith(
            id = idB,
            version = 2,
            mergeCandidateIds = Set(idC)
          )

          whenReady(workMatcher.matchWork(work)) { matcherResult =>
            assertRecent(matcherResult.createdTime)
            matcherResult.works shouldBe
              Set(
                MatchedIdentifiers(
                  Set(
                    WorkIdentifier(idA, version = 1),
                    WorkIdentifier(idB, version = 2),
                    WorkIdentifier(idC, version = 1))
                )
              )

            val savedNodes = scanTable[WorkNode](graphTable)
              .map(_.right.value)
              .toSet

            savedNodes shouldBe Set(
              workA
                .copy(
                  subgraphId = SubgraphId(idA, idB, idC),
                  componentIds = List(idA, idB, idC),
                ),
              workB
                .copy(
                  subgraphId = SubgraphId(idA, idB, idC),
                  componentIds = List(idA, idB, idC),
                )
                .updateSourceWork(version = 2, mergeCandidateIds = Set(idC)),
              workC
                .copy(
                  subgraphId = SubgraphId(idA, idB, idC),
                  componentIds = List(idA, idB, idC),
                ),
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
        val subgraphId = SubgraphId(idA, idB, idC)

        val (workA, workB, workC) = createThreeWorks("A->B->C")

        val future = workGraphStore.put(Set(workA, workB, workC))

        whenReady(future) { _ =>
          val work = createWorkWith(
            id = idA,
            mergeCandidateIds = Set(idB),
            version = workA.sourceWork.get.version + 1
          )

          implicit val lockDao: MemoryLockDao[String, UUID] =
            new MemoryLockDao[String, UUID] {
              override def lock(id: String, contextId: UUID): LockResult =
                synchronized {
                  if (id == subgraphId) {
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
      override def findAffectedWorks(
        ids: Set[CanonicalId]): Future[Set[WorkNode]] =
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

  // This is a regression test for the failure mode where we only lock over
  // the IDs we can see in the initial update, and not over IDs we discover
  // we need to lock after inspecting the graph store.
  //
  // This is best explained by the example below.
  it("locks over all the works affected in an update") {
    withWorkGraphTable { graphTable =>
      withWorkGraphStore(graphTable) { workGraphStore =>
        withWorkMatcher(workGraphStore) { workMatcher =>

          // We have three works:
          // (A) is standalone
          // (B) points to A
          // (C) points to B
          val workA = createWorkWith(id = idA)
          val workB = createWorkWith(id = idB, mergeCandidateIds = Set(idA))
          val workC = createWorkWith(id = idC, mergeCandidateIds = Set(idB))

          // First store work B in the graph.
          //
          // This will put two nodes in the graph: a node for B, and a stub for A.
          Await.result(workMatcher.matchWork(workB), atMost = 3 seconds)

          // Now try to store works A and C simultaneously.
          //
          // Here's how this can go wrong: when we get work C, we know we need
          // to lock at least {B, C}.  It's only when we inspect the existing graph
          // that we discover that we also need to link in A, so we should lock
          // that ID as well.  If we don't lock over A, we might blat the update
          // coming in work A.
          //
          // We need the locking to ensure we don't try to apply both updates at once.
          val futureA = workMatcher.matchWork(workA)
          val futureC = workMatcher.matchWork(workC)

          val resultA = Try { Await.result(futureA, atMost = 3 seconds) }
          val resultC = Try { Await.result(futureC, atMost = 3 seconds) }

          (resultA, resultC) match {
            // If one result succeeds and the other fails, that's fine -- the failed
            // result won't have written any data to the graph store, and will be
            // retried later.  We'll get consistent results.
            case (Success(_), Failure(e)) if e.getMessage.startsWith("FailedLock") => ()
            case (Failure(e), Success(_)) if e.getMessage.startsWith("FailedLock") => ()

            // It's possible for both updates to fail, depending on the exact timing.
            // Consider the following sequence:
            //
            //    1. matchWork(workA) locks 'A'
            //    2. matchWork(workC) locks 'C', 'B'
            //    3. matchWork(workA) tries to lock 'B', fails
            //    4. matchWork(workC) tries to lock 'A', fails
            //
            // Both updates would be retried later; the graph store remains consistent.
            case (Failure(e1), Failure(e2)) if e1.getMessage.startsWith("FailedLock") && e2.getMessage.startsWith("FailedLock") =>
              println(s"resultA = $resultA")
              println(s"resultC = $resultC")
              ()

            // If we get an unexpected failure or two successes, we might have inconsistent
            // data in the graph store.  Fail!
            case _ =>
              println(s"resultA = $resultA")
              println(s"resultC = $resultC")
              throw new RuntimeException("Both updates succeeded (or failed unexpectedly). This could lead to inconsistent data!")
          }
        }
      }
    }
  }

  private def getWorkNode(id: CanonicalId, graphTable: Table): WorkNode =
    getTableItem[WorkNode](id.underlying, graphTable).map(_.right.value).get
}
