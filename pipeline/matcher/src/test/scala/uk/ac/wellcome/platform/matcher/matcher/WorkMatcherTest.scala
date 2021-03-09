package uk.ac.wellcome.platform.matcher.matcher

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{spy, times, verify, when}
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
import uk.ac.wellcome.platform.matcher.exceptions.MatcherException
import uk.ac.wellcome.platform.matcher.fixtures.MatcherFixtures
import uk.ac.wellcome.platform.matcher.generators.WorkLinksGenerators
import uk.ac.wellcome.platform.matcher.models.{WorkGraph, WorkLinks}
import uk.ac.wellcome.platform.matcher.storage.WorkGraphStore
import uk.ac.wellcome.storage.locking.LockFailure
import uk.ac.wellcome.storage.locking.memory.{
  MemoryLockDao,
  MemoryLockingService
}

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
    with WorkLinksGenerators {

  private val identifierA = createIdentifier("A")
  private val identifierB = createIdentifier("B")
  private val identifierC = createIdentifier("C")

  it(
    "matches a work with no linked identifiers to itself only A and saves the updated graph A") {
    withWorkGraphTable { graphTable =>
      withWorkGraphStore(graphTable) { workGraphStore =>
        withWorkMatcher(workGraphStore) { workMatcher =>
          val id = createIdentifier("A")
          val links = createWorkLinksWith(id = id)

          whenReady(workMatcher.matchWork(links)) { matcherResult =>
            matcherResult shouldBe
              MatcherResult(Set(MatchedIdentifiers(
                Set(WorkIdentifier(links.workId, links.version)))))

            val savedLinkedWork =
              get[WorkNode](dynamoClient, graphTable.name)(
                "id" === links.workId)
                .map(_.value)

            savedLinkedWork shouldBe Some(
              WorkNode(links.workId, links.version, Nil, ciHash(links.workId)))
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
          val identifierA = createIdentifier("A")
          val identifierB = createIdentifier("B")

          val links = createWorkLinksWith(
            id = identifierA,
            referencedIds = Set(identifierB)
          )

          whenReady(workMatcher.matchWork(links)) { identifiersList =>
            identifiersList shouldBe
              MatcherResult(
                Set(
                  MatchedIdentifiers(Set(
                    WorkIdentifier(identifierA.canonicalId, links.version),
                    WorkIdentifier(identifierB.canonicalId, None)))))

            val savedWorkNodes = scan[WorkNode](dynamoClient, graphTable.name)
              .map(_.right.get)

            savedWorkNodes should contain theSameElementsAs List(
              WorkNode(
                identifierA.canonicalId,
                links.version,
                List(identifierB.canonicalId),
                ciHash(
                  List(identifierA.canonicalId, identifierB.canonicalId).sorted
                    .mkString("+"))
              ),
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

  it(
    "matches a previously stored work A->B with an update B->C and saves the graph A->B->C") {
    withWorkGraphTable { graphTable =>
      withWorkGraphStore(graphTable) { workGraphStore =>
        withWorkMatcher(workGraphStore) { workMatcher =>
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

          val links = createWorkLinksWith(
            id = identifierB,
            version = 2,
            referencedIds = Set(identifierC)
          )

          whenReady(workMatcher.matchWork(links)) { identifiersList =>
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

  it("throws MatcherException if it fails to lock primary works") {
    implicit val lockDao: MemoryLockDao[String, UUID] =
      new MemoryLockDao[String, UUID] {
        override def lock(id: String, contextId: UUID): LockResult =
          Left(LockFailure(id, e = new Throwable("BOOM!")))
      }

    val lockingService =
      new MemoryLockingService[Set[MatchedIdentifiers], Future]()

    withWorkGraphTable { graphTable =>
      withWorkGraphStore(graphTable) { workGraphStore =>
        val links = createWorkLinks

        val workMatcher = new WorkMatcher(workGraphStore, lockingService)

        val result = workMatcher.matchWork(links)

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
          WorkGraph(
            Set(
              WorkNode(idA, 0, List(idB), componentId),
              WorkNode(idB, 0, List(idC), componentId),
              WorkNode(idC, 0, Nil, componentId),
            )))

        whenReady(future) { _ =>
          val links = createWorkLinksWith(
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
            new MemoryLockingService[Set[MatchedIdentifiers], Future]()

          val workMatcher = new WorkMatcher(workGraphStore, lockingService)

          val result = workMatcher.matchWork(links)

          whenReady(result.failed) {
            _ shouldBe a[MatcherException]
          }
        }
      }
    }
  }

  it("fails if saving the updated links fails") {
    val mockWorkGraphStore = mock[WorkGraphStore]
    withWorkMatcher(mockWorkGraphStore) { workMatcher =>
      val expectedException = new RuntimeException("Failed to put")
      when(mockWorkGraphStore.findAffectedWorks(any[WorkLinks]))
        .thenReturn(Future.successful(WorkGraph(Set.empty)))
      when(mockWorkGraphStore.put(any[WorkGraph]))
        .thenThrow(expectedException)

      val links = createWorkLinks

      whenReady(workMatcher.matchWork(links).failed) { actualException =>
        actualException shouldBe MatcherException(expectedException)
      }
    }
  }

  it("skips writing to the store if there are no changes") {
    withWorkGraphTable { graphTable =>
      withWorkGraphStore(graphTable) { workGraphStore =>
        val spyStore = spy(workGraphStore)

        val links = createWorkLinks

        withWorkMatcher(spyStore) { workMatcher =>
          // Try to match the links more than once.  We have to match in sequence,
          // not in parallel, or the locking will block all but one of them from
          // doing anything non-trivial.
          val futures =
            workMatcher
              .matchWork(links)
              .flatMap { _ =>
                workMatcher.matchWork(links)
              }
              .flatMap { _ =>
                workMatcher.matchWork(links)
              }
              .flatMap { _ =>
                workMatcher.matchWork(links)
              }
              .flatMap { _ =>
                workMatcher.matchWork(links)
              }

          whenReady(futures) { _ =>
            verify(spyStore, times(1)).put(any[Set[WorkNode]])
          }
        }
      }
    }
  }
}
