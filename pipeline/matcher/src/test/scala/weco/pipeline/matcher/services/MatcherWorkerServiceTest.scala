package weco.pipeline.matcher.services

import org.scalatest.Assertion
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.messaging.fixtures.SQS
import weco.messaging.fixtures.SQS.QueuePair
import weco.messaging.memory.MemoryMessageSender
import weco.fixtures.TimeAssertions
import weco.pipeline.matcher.fixtures.MatcherFixtures
import weco.pipeline.matcher.generators.WorkStubGenerators
import weco.pipeline.matcher.models.{
  MatchedIdentifiers,
  MatcherResult,
  WorkIdentifier,
  WorkStub
}
import weco.pipeline.matcher.models.MatcherResult._
import weco.pipeline_storage.memory.MemoryRetriever

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class MatcherWorkerServiceTest
    extends AnyFunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with MatcherFixtures
    with WorkStubGenerators
    with TimeAssertions {

  it("matches a Work which doesn't reference any other Works") {
    val work = createWorkWith(id = idA)
    val expectedWorks =
      Set(
        MatchedIdentifiers(
          identifiers = Set(WorkIdentifier(work.id, version = work.version)))
      )

    implicit val retriever: MemoryRetriever[WorkStub] =
      new MemoryRetriever[WorkStub]()
    implicit val messageSender: MemoryMessageSender = new MemoryMessageSender()

    withLocalSqsQueue() { implicit queue =>
      withMatcherService(retriever, queue, messageSender) { _ =>
        processAndAssertMatchedWorkIs(work, expectedWorks = expectedWorks)
      }
    }
  }

  it("matches a Work that points to one other Work") {
    // Work Av1
    val workAv1 = createWorkWith(
      id = idA,
      version = 1,
      referencedWorkIds = Set(idB)
    )

    // Work Av1 matched to B (before B exists hence version is None)
    // need to match to works that do not exist to support
    // bi-directionally matched works without deadlocking (A->B, B->A)
    val expectedWorks =
      Set(
        MatchedIdentifiers(
          identifiers = Set(
            WorkIdentifier(idA, version = Some(1)),
            WorkIdentifier(idB, version = None)
          )
        )
      )

    implicit val retriever: MemoryRetriever[WorkStub] =
      new MemoryRetriever[WorkStub]()
    implicit val messageSender: MemoryMessageSender = new MemoryMessageSender()

    withLocalSqsQueue() { implicit queue =>
      withMatcherService(retriever, queue, messageSender) { _ =>
        processAndAssertMatchedWorkIs(workAv1, expectedWorks = expectedWorks)
      }
    }
  }

  it("matches Works together") {
    // Work Av1
    val workAv1 = createWorkWith(
      id = idA,
      version = 1
    )

    val expectedWorksAv1 =
      Set(
        MatchedIdentifiers(
          identifiers = Set(WorkIdentifier(idA, version = 1))
        )
      )

    // Work Bv1
    val workBv1 = createWorkWith(
      id = idB,
      version = 1
    )

    val expectedWorksBv1 =
      Set(
        MatchedIdentifiers(
          identifiers = Set(WorkIdentifier(idB, version = 1))
        )
      )

    // Work Av2 matched to B
    val workAv2 = createWorkWith(
      id = idA,
      version = 2,
      referencedWorkIds = Set(idB)
    )

    val expectedWorksAv2 =
      Set(
        MatchedIdentifiers(
          identifiers = Set(
            WorkIdentifier(idA, version = 2),
            WorkIdentifier(idB, version = 1)
          )
        )
      )

    // Work Cv1
    val workCv1 = createWorkWith(
      id = idC,
      version = 1
    )

    val expectedWorksCv1 =
      Set(
        MatchedIdentifiers(
          identifiers = Set(WorkIdentifier(idC, version = 1))
        )
      )

    // Work Bv2 matched to C
    val workBv2 = createWorkWith(
      id = idB,
      version = 2,
      referencedWorkIds = Set(idC)
    )

    val expectedWorksBv2 =
      Set(
        MatchedIdentifiers(
          identifiers = Set(
            WorkIdentifier(idA, version = 2),
            WorkIdentifier(idB, version = 2),
            WorkIdentifier(idC, version = 1)
          )
        )
      )

    implicit val retriever: MemoryRetriever[WorkStub] =
      new MemoryRetriever[WorkStub]()
    implicit val messageSender: MemoryMessageSender = new MemoryMessageSender()

    withLocalSqsQueue() { implicit queue =>
      withMatcherService(retriever, queue, messageSender) { _ =>
        processAndAssertMatchedWorkIs(workAv1, expectedWorksAv1)
        processAndAssertMatchedWorkIs(workBv1, expectedWorksBv1)
        processAndAssertMatchedWorkIs(workAv2, expectedWorksAv2)
        processAndAssertMatchedWorkIs(workCv1, expectedWorksCv1)
        processAndAssertMatchedWorkIs(workBv2, expectedWorksBv2)
      }
    }
  }

  it("breaks matched works into individual works") {
    // Work Av1
    val workAv1 = createWorkWith(
      id = idA,
      version = 1
    )

    val expectedWorksAv1 =
      Set(
        MatchedIdentifiers(
          identifiers = Set(WorkIdentifier(idA, version = 1))
        )
      )

    // Work Bv1
    val workBv1 = createWorkWith(
      id = idB,
      version = 1
    )

    val expectedWorksBv1 =
      Set(
        MatchedIdentifiers(
          identifiers = Set(WorkIdentifier(idB, version = 1))
        )
      )

    // Match Work A to Work B
    val workAv2MatchedToB =
      createWorkWith(
        id = idA,
        version = 2,
        referencedWorkIds = Set(idB)
      )

    val expectedWorksAv2MatchedToB =
      Set(
        MatchedIdentifiers(
          identifiers = Set(
            WorkIdentifier(idA, version = 2),
            WorkIdentifier(idB, version = 1)
          )
        )
      )

    // A no longer matches B
    val workAv3WithNoMatchingWorks =
      createWorkWith(
        id = idA,
        version = 3
      )

    val expectedWorksAv3 =
      Set(
        MatchedIdentifiers(
          identifiers = Set(WorkIdentifier(idA, version = 3))
        ),
        MatchedIdentifiers(
          identifiers = Set(WorkIdentifier(idB, version = 1))
        )
      )

    implicit val retriever: MemoryRetriever[WorkStub] =
      new MemoryRetriever[WorkStub]()
    implicit val messageSender: MemoryMessageSender = new MemoryMessageSender()

    withLocalSqsQueue() { implicit queue =>
      withMatcherService(retriever, queue, messageSender) { _ =>
        processAndAssertMatchedWorkIs(workAv1, expectedWorksAv1)
        processAndAssertMatchedWorkIs(workBv1, expectedWorksBv1)
        processAndAssertMatchedWorkIs(
          workAv2MatchedToB,
          expectedWorksAv2MatchedToB)
        processAndAssertMatchedWorkIs(
          workAv3WithNoMatchingWorks,
          expectedWorksAv3)
      }
    }
  }

  it("does not match a lower version") {
    val workAv2 = createWorkWith(
      id = idA,
      version = 2
    )

    val expectedWorkAv2 =
      Set(
        MatchedIdentifiers(
          identifiers = Set(WorkIdentifier(idA, version = 2))
        )
      )

    implicit val retriever: MemoryRetriever[WorkStub] =
      new MemoryRetriever[WorkStub]()
    implicit val messageSender: MemoryMessageSender = new MemoryMessageSender()

    withLocalSqsQueuePair() {
      case QueuePair(queue, dlq) =>
        implicit val q: SQS.Queue = queue

        withMatcherService(retriever, queue, messageSender) { _ =>
          processAndAssertMatchedWorkIs(workAv2, expectedWorkAv2)

          // Work V1 is sent but not matched
          val workAv1 =
            createWorkWith(
              id = idA,
              version = 1
            )

          sendWork(workAv1, retriever, queue)
          eventually {
            assertQueueEmpty(queue)
            assertQueueEmpty(dlq)

            messageSender
              .getMessages[MatcherResult]
              .last
              .works shouldBe expectedWorkAv2
          }
        }
    }
  }

  it("does not match an existing version with different information") {
    val workAv2 = createWorkWith(
      id = idA,
      version = 2
    )

    val expectedWorkAv2 =
      Set(
        MatchedIdentifiers(
          identifiers = Set(WorkIdentifier(idA, version = 2))
        )
      )

    implicit val retriever: MemoryRetriever[WorkStub] =
      new MemoryRetriever[WorkStub]()
    implicit val messageSender: MemoryMessageSender = new MemoryMessageSender()

    withLocalSqsQueuePair(visibilityTimeout = 1 second) {
      case QueuePair(queue, dlq) =>
        implicit val q: SQS.Queue = queue

        withMatcherService(retriever, queue, messageSender) { _ =>
          processAndAssertMatchedWorkIs(workAv2, expectedWorkAv2)

          // Work V1 is sent but not matched
          val differentWorkAv2 =
            createWorkWith(
              id = idA,
              version = 2,
              referencedWorkIds = Set(idB)
            )

          sendWork(differentWorkAv2, retriever, queue)
          eventually {
            assertQueueEmpty(queue)
            assertQueueHasSize(dlq, size = 1)
          }
        }
    }
  }

  private def processAndAssertMatchedWorkIs(
    work: WorkStub,
    expectedWorks: Set[MatchedIdentifiers])(
    implicit
    retriever: MemoryRetriever[WorkStub],
    queue: SQS.Queue,
    messageSender: MemoryMessageSender
  ): Assertion = {
    sendWork(work, retriever, queue)
    eventually {
      val result = messageSender.getMessages[MatcherResult].last

      result.works shouldBe expectedWorks
      assertRecent(result.createdTime)
    }
  }
}
