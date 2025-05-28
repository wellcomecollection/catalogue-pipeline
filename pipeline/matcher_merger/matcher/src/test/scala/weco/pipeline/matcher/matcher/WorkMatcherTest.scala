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
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
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
    "matches a work with no linked identifiers to itself only A and saves the updated graph A"
  ) {
    withWorkGraphTable {
      graphTable =>
        withWorkGraphStore(graphTable) {
          workGraphStore =>
            withWorkMatcher(workGraphStore) {
              workMatcher =>
                val work = createWorkWith(id = idA)

                whenReady(workMatcher.matchWork(work)) {
                  matcherResult =>
                    assertRecent(matcherResult.createdTime)
                    matcherResult.works shouldBe
                      Set(
                        MatchedIdentifiers(
                          Set(WorkIdentifier(work.id, work.version))
                        )
                      )

                    val savedLinkedWork = getWorkNode(work.id, graphTable)

                    savedLinkedWork shouldBe
                      WorkNode(
                        id = work.id,
                        subgraphId = SubgraphId(work.id),
                        componentIds = List(work.id),
                        sourceWork = SourceWorkData(
                          id = work.state.sourceIdentifier,
                          version = work.version
                        )
                      )
                }
            }
        }
    }
  }

  it(
    "matches a work with a single linked identifier A->B and saves the graph A->B"
  ) {
    withWorkGraphTable {
      graphTable =>
        withWorkGraphStore(graphTable) {
          workGraphStore =>
            withWorkMatcher(workGraphStore) {
              workMatcher =>
                val work = createWorkWith(
                  id = idA,
                  mergeCandidateIds = Set(idB)
                )

                whenReady(workMatcher.matchWork(work)) {
                  matcherResult =>
                    assertRecent(matcherResult.createdTime)
                    matcherResult.works shouldBe
                      Set(
                        MatchedIdentifiers(
                          Set(WorkIdentifier(idA, work.version))
                        )
                      )

                    val savedWorkNodes = scanTable[WorkNode](graphTable)
                      .map(_.value)

                    savedWorkNodes should contain theSameElementsAs List(
                      WorkNode(
                        id = idA,
                        subgraphId = SubgraphId(idA, idB),
                        componentIds = List(idA, idB),
                        sourceWork = SourceWorkData(
                          id = work.state.sourceIdentifier,
                          version = work.version,
                          mergeCandidateIds = List(idB)
                        )
                      ),
                      WorkNode(
                        id = idB,
                        subgraphId = SubgraphId(idA, idB),
                        componentIds = List(idA, idB)
                      )
                    )
                }
            }
        }
    }
  }

  it(
    "matches a previously stored work A->B with an update B->C and saves the graph A->B->C"
  ) {
    withWorkGraphTable {
      graphTable =>
        withWorkGraphStore(graphTable) {
          workGraphStore =>
            withWorkMatcher(workGraphStore) {
              workMatcher =>
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

                whenReady(workMatcher.matchWork(work)) {
                  matcherResult =>
                    assertRecent(matcherResult.createdTime)
                    matcherResult.works shouldBe
                      Set(
                        MatchedIdentifiers(
                          Set(
                            WorkIdentifier(idA, version = 1),
                            WorkIdentifier(idB, version = 2),
                            WorkIdentifier(idC, version = 1)
                          )
                        )
                      )

                    val savedNodes = scanTable[WorkNode](graphTable)
                      .map(_.value)
                      .toSet

                    savedNodes shouldBe Set(
                      workA
                        .copy(
                          subgraphId = SubgraphId(idA, idB, idC),
                          componentIds = List(idA, idB, idC)
                        ),
                      workB
                        .copy(
                          subgraphId = SubgraphId(idA, idB, idC),
                          componentIds = List(idA, idB, idC)
                        )
                        .updateSourceWork(
                          version = 2,
                          mergeCandidateIds = Set(idC)
                        ),
                      workC
                        .copy(
                          subgraphId = SubgraphId(idA, idB, idC),
                          componentIds = List(idA, idB, idC)
                        )
                    )
                }
            }
        }
    }
  }

  it(
    "doesn't match through a suppressed Sierra e-bib"
  ) {
    // This test covers the case where we have three works which are notionally
    // connected:
    //
    //    (Sierra physical bib)
    //              |
    //    (Sierra digitised bib)
    //              |
    //    (Digitised METS record)
    //
    // If the digitised bib is suppressed in Sierra, we won't be able to create a
    // IIIF Presentation manifest or display a digitised item.  We shouldn't match
    // through the digitised bib.  They should be returned as three distinct works.
    //
    withWorkGraphTable {
      graphTable =>
        withWorkGraphStore(graphTable) {
          workGraphStore =>
            withWorkMatcher(workGraphStore) {
              workMatcher =>
                val work = createWorkWith(id = idA)

                whenReady(workMatcher.matchWork(work)) {
                  matcherResult =>
                    assertRecent(matcherResult.createdTime)
                    matcherResult.works shouldBe
                      Set(
                        MatchedIdentifiers(
                          Set(WorkIdentifier(work.id, work.version))
                        )
                      )

                    val savedLinkedWork = getWorkNode(work.id, graphTable)

                    savedLinkedWork shouldBe
                      WorkNode(
                        id = work.id,
                        subgraphId = SubgraphId(work.id),
                        componentIds = List(work.id),
                        sourceWork = SourceWorkData(
                          id = work.state.sourceIdentifier,
                          version = work.version
                        )
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

    withWorkGraphTable {
      graphTable =>
        withWorkGraphStore(graphTable) {
          workGraphStore =>
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

    withWorkGraphTable {
      graphTable =>
        withWorkGraphStore(graphTable) {
          workGraphStore =>
            val subgraphId = SubgraphId(idA, idB, idC)

            val (workA, workB, workC) = createThreeWorks("A->B->C")

            val future = workGraphStore.put(Set(workA, workB, workC))

            whenReady(future) {
              _ =>
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

                val workMatcher =
                  new WorkMatcher(workGraphStore, lockingService)

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
        ids: Set[CanonicalId]
      ): Future[Set[WorkNode]] =
        Future.successful(Set[WorkNode]())

      override def put(nodes: Set[WorkNode]): Future[Unit] =
        Future.failed(expectedException)
    }

    withWorkMatcher(brokenStore) {
      workMatcher =>
        val work = createWorkStub

        whenReady(workMatcher.matchWork(work).failed) {
          _.getMessage shouldBe expectedException.getMessage
        }
    }
  }

  it("skips writing to the store if there are no changes") {
    withWorkGraphTable {
      graphTable =>
        withWorkNodeDao(graphTable) {
          workNodeDao =>
            var putCount = 0

            val spyStore = new WorkGraphStore(workNodeDao) {
              override def put(nodes: Set[WorkNode]): Future[Unit] = {
                putCount += 1
                super.put(nodes)
              }
            }

            val work = createWorkStub

            withWorkMatcher(spyStore) {
              workMatcher =>
                // Try to match the work more than once.  We have to match in sequence,
                // not in parallel, or the locking will block all but one of them from
                // doing anything non-trivial.
                val futures =
                  workMatcher
                    .matchWork(work)
                    .flatMap {
                      _ =>
                        workMatcher.matchWork(work)
                    }
                    .flatMap {
                      _ =>
                        workMatcher.matchWork(work)
                    }
                    .flatMap {
                      _ =>
                        workMatcher.matchWork(work)
                    }
                    .flatMap {
                      _ =>
                        workMatcher.matchWork(work)
                    }

                whenReady(futures) {
                  _ =>
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
  // This is difficult to explain in the abstract, and is easiest to understand
  // with an example.  Consider the following three works:
  //
  //      C -> B -> A
  //
  // We send them to the matcher in the following order:
  //
  // B, wait, then
  // A, C simultaneously.
  //
  // We're trying to catch a very particular race condition where the update to C
  // gets a stale value from A from the store, and blats the update to A.
  //
  it("locks over all the works affected in an update") {
    withWorkGraphTable {
      graphTable =>
        withWorkNodeDao(graphTable) {
          workNodeDao =>
            // We take control of the lock dao here to ensure a very precise
            // sequence of events:
            //
            //    1.  The matcher starts processing the update to A.
            //    2.  It prepares what it's going to write to the graph store,
            //        but it doesn't take an expanded lock. (*)
            //    3.  The matcher starts processing the update to A.
            //        It reads the old value of A/B from the store.
            //    4.  Now we allow the update to 'A' to write the new values
            //        to the store.
            //    5.  The matcher prepares to write the new value of C, but doesn't
            //        do so until the update to 'A' is finished. (**)
            //
            var createdLocksHistory: List[String] = List()
            var cHasReadGraph = false
            var specialLockingBehaviour = false
            val workGraphStore = new WorkGraphStore(workNodeDao) {
              override def findAffectedWorks(
                ids: Set[CanonicalId]
              ): Future[Set[WorkNode]] = {
                if (specialLockingBehaviour) {
                  if (ids == Set(idB, idC)) cHasReadGraph = true
                }
                super.findAffectedWorks(ids)
              }
            }

            implicit val lockDao: MemoryLockDao[String, UUID] =
              new MemoryLockDao[String, UUID] {
                override def lock(id: String, contextId: UUID): LockResult = {
                  if (specialLockingBehaviour) {
                    // (*) We don't let the update to 'A' start writing graph updates until
                    // we know the update to 'C' has read the old state of the graph
                    if (
                      id == SubgraphId(idA, idB) && createdLocksHistory.count(
                        _ == SubgraphId(idA, idB)
                      ) == 0
                    ) {
                      // Lock before waiting for C, so that when the locks for C come in
                      // at roughly the same time, the lock is already in place.
                      // If we do not lock here, then the wait for A to unlock may
                      // not take place.
                      val lock = super.lock(id, contextId)
                      createdLocksHistory = createdLocksHistory :+ id

                      eventually(timeout = timeout(5 seconds)) {
                        if (!cHasReadGraph) throw new Exception("not yet")
                      }
                      lock
                    }

                    // (**) We don't let the update to 'C' start writing graph updates until
                    // we know the update to 'A' is finished
                    else {
                      if (
                        (id == SubgraphId(idA, idB) || id == SubgraphId(
                          idA,
                          idB,
                          idC
                        )) &&
                        createdLocksHistory.count(
                          _ == SubgraphId(idA, idB)
                        ) == 1
                      ) {
                        eventually(timeout = timeout(5 seconds)) {
                          if (locks.contains(idA.underlying))
                            throw new Exception("not yet")
                        }
                      }
                      createdLocksHistory = createdLocksHistory :+ id
                      super.lock(id, contextId)
                    }
                  } else {
                    super.lock(id, contextId)
                  }
                }
              }

            val workMatcher = new WorkMatcher(
              workGraphStore = workGraphStore,
              lockingService = new MemoryLockingService[MatcherResult, Future]()
            )

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
            Await.result(workMatcher.matchWork(workB), atMost = 5 seconds)
            specialLockingBehaviour = true
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
            // Sleep briefly to ensure that A is first through the gate.
            // This is just to ensure that when we execute the artificial waits in the mock
            // lock DAO above, A waits C to start, then C waits for A to finish.
            // If we do not do this, then it is nondeterministic as to which one locks first
            // if C goes through first, then it will not wait for anything and the test
            // will fail without proving anything useful.
            Thread.sleep(100)
            val futureC = workMatcher.matchWork(workC)

            val resultA = Try {
              Await.result(futureA, atMost = 5 seconds)
            }
            val resultC = Try {
              Await.result(futureC, atMost = 5 seconds)
            }

            // The update to A should have succeeded; the update to B should have failed
            // because of inconsistent data.
            resultA shouldBe a[Success[_]]

            resultC shouldBe a[Failure[_]]
            resultC
              .asInstanceOf[Failure[_]]
              .exception
              .getMessage should include(
              "graph store contents changed during matching"
            )

            // If the update to A was successful, we should see the 'sourceWork' field
            // for A is populated.  If not, this will fail.
            getWorkNode(idA, graphTable).sourceWork shouldBe defined
        }
    }
  }

  private def getWorkNode(id: CanonicalId, graphTable: Table): WorkNode =
    getTableItem[WorkNode](id.underlying, graphTable).map(_.value).get
}
