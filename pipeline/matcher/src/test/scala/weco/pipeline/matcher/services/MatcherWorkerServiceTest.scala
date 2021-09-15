package weco.pipeline.matcher.services

import org.scalatest.Assertion
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.json.JsonUtil._
import weco.messaging.fixtures.SQS
import weco.messaging.fixtures.SQS.QueuePair
import weco.messaging.memory.MemoryMessageSender
import weco.fixtures.TimeAssertions
import weco.pipeline.matcher.fixtures.MatcherFixtures
import weco.pipeline.matcher.generators.WorkStubGenerators
import weco.pipeline.matcher.models.{
  MatchedIdentifiers,
  MatcherResult,
  WorkStub
}
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

  private val identifierA = createIdentifier("AAAAAAAA")
  private val identifierB = createIdentifier("BBBBBBBB")
  private val identifierC = createIdentifier("CCCCCCCC")

  it("matches a Work which doesn't reference any other Works") {
    val work = createWorkStubWith(id = identifierA)
    val expectedWorks =
      Set(
        MatchedIdentifiers(workCollections = Set(work))
      )

    implicit val retriever: MemoryRetriever[WorkStub] =
      new MemoryRetriever[WorkStub]()
    implicit val messageSender: MemoryMessageSender = new MemoryMessageSender()

    withLocalSqsQueue() { implicit queue =>
      withWorkerService(retriever, queue, messageSender) { _ =>
        processAndAssertMatchedWorkIs(work, expectedWorks = expectedWorks)
      }
    }
  }

  it("matches a Work that points to one other Work") {
    // Work Av1
    val workLinksAv1 = createWorkStubWith(
      id = identifierA,
      modifiedTime = modifiedTime1,
      referencedIds = Set(identifierB)
    )

    // Work Av1 matched to B (before B exists hence version is None)
    // need to match to works that do not exist to support
    // bi-directionally matched works without deadlocking (A->B, B->A)
    val expectedWorks =
      Set(
        MatchedIdentifiers(
          workCollections = Set(
            WorkStub(identifierA.canonicalId, modifiedTime = modifiedTime1)
          )
        )
      )

    implicit val retriever: MemoryRetriever[WorkStub] =
      new MemoryRetriever[WorkStub]()
    implicit val messageSender: MemoryMessageSender = new MemoryMessageSender()

    withLocalSqsQueue() { implicit queue =>
      withWorkerService(retriever, queue, messageSender) { _ =>
        processAndAssertMatchedWorkIs(
          workLinksAv1,
          expectedWorks = expectedWorks)
      }
    }
  }

  it("matches Works together") {
    // Work Av1
    val workLinksAv1 = createWorkStubWith(
      id = identifierA,
      modifiedTime = modifiedTime1
    )

    val expectedWorksAv1 =
      Set(
        MatchedIdentifiers(
          workCollections =
            Set(WorkStub(identifierA.canonicalId, modifiedTime = modifiedTime1))
        )
      )

    // Work Bv1
    val workLinksBv1 = createWorkStubWith(
      id = identifierB,
      modifiedTime = modifiedTime1
    )

    val expectedWorksBv1 =
      Set(
        MatchedIdentifiers(
          workCollections =
            Set(WorkStub(identifierB.canonicalId, modifiedTime = modifiedTime1))
        )
      )

    // Work Av2 matched to B
    val workLinksAv2 = createWorkStubWith(
      id = identifierA,
      modifiedTime = modifiedTime2,
      referencedIds = Set(identifierB)
    )

    val expectedWorksAv2 =
      Set(
        MatchedIdentifiers(
          workCollections = Set(
            WorkStub(identifierA.canonicalId, modifiedTime = modifiedTime2),
            WorkStub(identifierB.canonicalId, modifiedTime = modifiedTime1)
          )
        )
      )

    // Work Cv1
    val workLinksCv1 = createWorkStubWith(
      id = identifierC,
      modifiedTime = modifiedTime1
    )

    val expectedWorksCv1 =
      Set(
        MatchedIdentifiers(
          workCollections =
            Set(WorkStub(identifierC.canonicalId, modifiedTime = modifiedTime1))
        )
      )

    // Work Bv2 matched to C
    val workLinksBv2 = createWorkStubWith(
      id = identifierB,
      modifiedTime = modifiedTime2,
      referencedIds = Set(identifierC)
    )

    val expectedWorksBv2 =
      Set(
        MatchedIdentifiers(
          workCollections = Set(
            WorkStub(identifierA.canonicalId, modifiedTime = modifiedTime2),
            WorkStub(identifierB.canonicalId, modifiedTime = modifiedTime2),
            WorkStub(identifierC.canonicalId, modifiedTime = modifiedTime1)
          )
        )
      )

    implicit val retriever: MemoryRetriever[WorkStub] =
      new MemoryRetriever[WorkStub]()
    implicit val messageSender: MemoryMessageSender = new MemoryMessageSender()

    withLocalSqsQueue() { implicit queue =>
      withWorkerService(retriever, queue, messageSender) { _ =>
        processAndAssertMatchedWorkIs(workLinksAv1, expectedWorksAv1)
        processAndAssertMatchedWorkIs(workLinksBv1, expectedWorksBv1)
        processAndAssertMatchedWorkIs(workLinksAv2, expectedWorksAv2)
        processAndAssertMatchedWorkIs(workLinksCv1, expectedWorksCv1)
        processAndAssertMatchedWorkIs(workLinksBv2, expectedWorksBv2)
      }
    }
  }

  it("breaks matched works into individual works") {
    // Work Av1
    val workLinksAv1 = createWorkStubWith(
      id = identifierA,
      modifiedTime = modifiedTime1
    )

    val expectedWorksAv1 =
      Set(
        MatchedIdentifiers(
          workCollections =
            Set(WorkStub(identifierA.canonicalId, modifiedTime = modifiedTime1))
        )
      )

    // Work Bv1
    val workLinksBv1 = createWorkStubWith(
      id = identifierB,
      modifiedTime = modifiedTime1
    )

    val expectedWorksBv1 =
      Set(
        MatchedIdentifiers(
          workCollections =
            Set(WorkStub(identifierB.canonicalId, modifiedTime = modifiedTime1))
        )
      )

    // Match Work A to Work B
    val workLinksAv2MatchedToB =
      createWorkStubWith(
        id = identifierA,
        modifiedTime = modifiedTime2,
        referencedIds = Set(identifierB)
      )

    val expectedWorksAv2MatchedToB =
      Set(
        MatchedIdentifiers(
          workCollections = Set(
            WorkStub(identifierA.canonicalId, modifiedTime = modifiedTime2),
            WorkStub(identifierB.canonicalId, modifiedTime = modifiedTime1)
          )
        )
      )

    // A no longer matches B
    val workLinksAv3WithNoMatchingWorks =
      createWorkStubWith(
        id = identifierA,
        modifiedTime = modifiedTime3
      )

    val expectedWorksAv3 =
      Set(
        MatchedIdentifiers(
          workCollections =
            Set(WorkStub(identifierA.canonicalId, modifiedTime = modifiedTime3))
        ),
        MatchedIdentifiers(
          workCollections =
            Set(WorkStub(identifierB.canonicalId, modifiedTime = modifiedTime1))
        )
      )

    implicit val retriever: MemoryRetriever[WorkStub] =
      new MemoryRetriever[WorkStub]()
    implicit val messageSender: MemoryMessageSender = new MemoryMessageSender()

    withLocalSqsQueue() { implicit queue =>
      withWorkerService(retriever, queue, messageSender) { _ =>
        processAndAssertMatchedWorkIs(workLinksAv1, expectedWorksAv1)
        processAndAssertMatchedWorkIs(workLinksBv1, expectedWorksBv1)
        processAndAssertMatchedWorkIs(
          workLinksAv2MatchedToB,
          expectedWorksAv2MatchedToB)
        processAndAssertMatchedWorkIs(
          workLinksAv3WithNoMatchingWorks,
          expectedWorksAv3)
      }
    }
  }

  it("does not match a lower version") {
    val workLinksAv2 = createWorkStubWith(
      id = identifierA,
      modifiedTime = modifiedTime2
    )

    val expectedWorkAv2 =
      Set(
        MatchedIdentifiers(
          workCollections =
            Set(WorkStub(identifierA.canonicalId, modifiedTime = modifiedTime2))
        )
      )

    implicit val retriever: MemoryRetriever[WorkStub] =
      new MemoryRetriever[WorkStub]()
    implicit val messageSender: MemoryMessageSender = new MemoryMessageSender()

    withLocalSqsQueuePair() {
      case QueuePair(queue, dlq) =>
        implicit val q: SQS.Queue = queue

        withWorkerService(retriever, queue, messageSender) { _ =>
          processAndAssertMatchedWorkIs(workLinksAv2, expectedWorkAv2)

          // Work V1 is sent but not matched
          val workLinksAv1 =
            createWorkStubWith(
              id = identifierA,
              modifiedTime = modifiedTime1
            )

          sendWork(workLinksAv1, retriever, queue)
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
    val workLinksAv2 = createWorkStubWith(
      id = identifierA,
      modifiedTime = modifiedTime2
    )

    val expectedWorkAv2 =
      Set(
        MatchedIdentifiers(
          workCollections =
            Set(WorkStub(identifierA.canonicalId, modifiedTime = modifiedTime2))
        )
      )

    implicit val retriever: MemoryRetriever[WorkStub] =
      new MemoryRetriever[WorkStub]()
    implicit val messageSender: MemoryMessageSender = new MemoryMessageSender()

    withLocalSqsQueuePair(visibilityTimeout = 1 second) {
      case QueuePair(queue, dlq) =>
        implicit val q: SQS.Queue = queue

        withWorkerService(retriever, queue, messageSender) { _ =>
          processAndAssertMatchedWorkIs(workLinksAv2, expectedWorkAv2)

          // Work V1 is sent but not matched
          val differentWorkLinksAv2 =
            createWorkStubWith(
              id = identifierA,
              modifiedTime = modifiedTime2,
              referencedIds = Set(identifierB)
            )

          sendWork(differentWorkLinksAv2, retriever, queue)
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
