package uk.ac.wellcome.platform.matcher.services

import org.scalatest.Assertion
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.matcher.{
  MatchedIdentifiers,
  MatcherResult,
  WorkIdentifier
}
import uk.ac.wellcome.platform.matcher.fixtures.MatcherFixtures
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.pipeline_storage.MemoryRetriever
import uk.ac.wellcome.platform.matcher.generators.WorkLinksGenerators
import uk.ac.wellcome.platform.matcher.models.WorkLinks

import scala.concurrent.ExecutionContext.Implicits.global

class MatcherWorkerServiceTest
    extends AnyFunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with MatcherFixtures
    with WorkLinksGenerators {

  private val identifierA = createIdentifier("AAAAAAAA")
  private val identifierB = createIdentifier("BBBBBBBB")
  private val identifierC = createIdentifier("CCCCCCCC")

  it("matches a Work which doesn't reference any other Works") {
    val workLinks = createWorkLinksWith(id = identifierA)
    val expectedResult =
      MatcherResult(
        Set(
          MatchedIdentifiers(identifiers =
            Set(WorkIdentifier(workLinks.workId, version = workLinks.version)))
        )
      )

    implicit val retriever: MemoryRetriever[WorkLinks] =
      new MemoryRetriever[WorkLinks]()
    implicit val messageSender: MemoryMessageSender = new MemoryMessageSender()

    withLocalSqsQueue() { implicit queue =>
      withWorkerService(retriever, queue, messageSender) { _ =>
        processAndAssertMatchedWorkIs(
          workLinks,
          expectedResult = expectedResult)
      }
    }
  }

  it("matches a Work that points to one other Work") {
    // Work Av1
    val workLinksAv1 = createWorkLinksWith(
      id = identifierA,
      version = 1,
      referencedIds = Set(identifierB)
    )

    // Work Av1 matched to B (before B exists hence version is None)
    // need to match to works that do not exist to support
    // bi-directionally matched works without deadlocking (A->B, B->A)
    val expectedMatchedWorks = MatcherResult(
      Set(
        MatchedIdentifiers(
          identifiers = Set(
            WorkIdentifier(identifierA.canonicalId, version = Some(1)),
            WorkIdentifier(identifierB.canonicalId, version = None)
          )
        )
      )
    )

    implicit val retriever: MemoryRetriever[WorkLinks] =
      new MemoryRetriever[WorkLinks]()
    implicit val messageSender: MemoryMessageSender = new MemoryMessageSender()

    withLocalSqsQueue() { implicit queue =>
      withWorkerService(retriever, queue, messageSender) { _ =>
        processAndAssertMatchedWorkIs(
          workLinksAv1,
          expectedResult = expectedMatchedWorks)
      }
    }
  }

  it("matches Works together") {
    // Work Av1
    val workLinksAv1 = createWorkLinksWith(
      id = identifierA,
      version = 1
    )

    val expectedMatchedWorksAv1 = MatcherResult(
      Set(
        MatchedIdentifiers(
          identifiers =
            Set(WorkIdentifier(identifierA.canonicalId, version = 1))
        )
      )
    )

    // Work Bv1
    val workLinksBv1 = createWorkLinksWith(
      id = identifierB,
      version = 1
    )

    val expectedMatchedWorksBv1 = MatcherResult(
      Set(
        MatchedIdentifiers(
          identifiers =
            Set(WorkIdentifier(identifierB.canonicalId, version = 1))
        )
      )
    )

    // Work Av2 matched to B
    val workLinksAv2 = createWorkLinksWith(
      id = identifierA,
      version = 2,
      referencedIds = Set(identifierB)
    )

    val expectedMatchedWorksAv2 = MatcherResult(
      Set(
        MatchedIdentifiers(
          identifiers = Set(
            WorkIdentifier(identifierA.canonicalId, version = 2),
            WorkIdentifier(identifierB.canonicalId, version = 1)
          )
        )
      )
    )

    // Work Cv1
    val workLinksCv1 = createWorkLinksWith(
      id = identifierC,
      version = 1
    )

    val expectedMatcherWorksCv1 =
      MatcherResult(
        Set(
          MatchedIdentifiers(
            identifiers =
              Set(WorkIdentifier(identifierC.canonicalId, version = 1))
          )
        )
      )

    // Work Bv2 matched to C
    val workLinksBv2 = createWorkLinksWith(
      id = identifierB,
      version = 2,
      referencedIds = Set(identifierC)
    )

    val expectedMatchedWorksBv2 =
      MatcherResult(
        Set(
          MatchedIdentifiers(
            identifiers = Set(
              WorkIdentifier(identifierA.canonicalId, version = 2),
              WorkIdentifier(identifierB.canonicalId, version = 2),
              WorkIdentifier(identifierC.canonicalId, version = 1)
            )
          )
        )
      )

    implicit val retriever: MemoryRetriever[WorkLinks] =
      new MemoryRetriever[WorkLinks]()
    implicit val messageSender: MemoryMessageSender = new MemoryMessageSender()

    withLocalSqsQueue() { implicit queue =>
      withWorkerService(retriever, queue, messageSender) { _ =>
        processAndAssertMatchedWorkIs(workLinksAv1, expectedMatchedWorksAv1)
        processAndAssertMatchedWorkIs(workLinksBv1, expectedMatchedWorksBv1)
        processAndAssertMatchedWorkIs(workLinksAv2, expectedMatchedWorksAv2)
        processAndAssertMatchedWorkIs(workLinksCv1, expectedMatcherWorksCv1)
        processAndAssertMatchedWorkIs(workLinksBv2, expectedMatchedWorksBv2)
      }
    }
  }

  it("breaks matched works into individual works") {
    // Work Av1
    val workLinksAv1 = createWorkLinksWith(
      id = identifierA,
      version = 1
    )

    val expectedMatchedWorksAv1 = MatcherResult(
      Set(
        MatchedIdentifiers(
          identifiers =
            Set(WorkIdentifier(identifierA.canonicalId, version = 1))
        )
      )
    )

    // Work Bv1
    val workLinksBv1 = createWorkLinksWith(
      id = identifierB,
      version = 1
    )

    val expectedMatchedWorksBv1 = MatcherResult(
      Set(
        MatchedIdentifiers(
          identifiers =
            Set(WorkIdentifier(identifierB.canonicalId, version = 1))
        )
      )
    )

    // Match Work A to Work B
    val workLinksAv2MatchedToB =
      createWorkLinksWith(
        id = identifierA,
        version = 2,
        referencedIds = Set(identifierB)
      )

    val expectedMatchedWorksAv2MatchedToB =
      MatcherResult(
        Set(
          MatchedIdentifiers(
            identifiers = Set(
              WorkIdentifier(identifierA.canonicalId, version = 2),
              WorkIdentifier(identifierB.canonicalId, version = 1)
            )
          )
        )
      )

    // A no longer matches B
    val workLinksAv3WithNoMatchingWorks =
      createWorkLinksWith(
        id = identifierA,
        version = 3
      )

    val expectedMatchedWorksAv3 =
      MatcherResult(
        Set(
          MatchedIdentifiers(
            identifiers =
              Set(WorkIdentifier(identifierA.canonicalId, version = 3))
          ),
          MatchedIdentifiers(
            identifiers =
              Set(WorkIdentifier(identifierB.canonicalId, version = 1))
          )
        )
      )

    implicit val retriever: MemoryRetriever[WorkLinks] =
      new MemoryRetriever[WorkLinks]()
    implicit val messageSender: MemoryMessageSender = new MemoryMessageSender()

    withLocalSqsQueue() { implicit queue =>
      withWorkerService(retriever, queue, messageSender) { _ =>
        processAndAssertMatchedWorkIs(workLinksAv1, expectedMatchedWorksAv1)
        processAndAssertMatchedWorkIs(workLinksBv1, expectedMatchedWorksBv1)
        processAndAssertMatchedWorkIs(
          workLinksAv2MatchedToB,
          expectedMatchedWorksAv2MatchedToB)
        processAndAssertMatchedWorkIs(
          workLinksAv3WithNoMatchingWorks,
          expectedMatchedWorksAv3)
      }
    }
  }

  it("does not match a lower version") {
    val workLinksAv2 = createWorkLinksWith(
      id = identifierA,
      version = 2
    )

    val expectedMatchedWorkAv2 = MatcherResult(
      Set(
        MatchedIdentifiers(
          identifiers =
            Set(WorkIdentifier(identifierA.canonicalId, version = 2))
        )
      )
    )

    implicit val retriever: MemoryRetriever[WorkLinks] =
      new MemoryRetriever[WorkLinks]()
    implicit val messageSender: MemoryMessageSender = new MemoryMessageSender()

    withLocalSqsQueuePair() {
      case QueuePair(queue, dlq) =>
        implicit val q: SQS.Queue = queue

        withWorkerService(retriever, queue, messageSender) { _ =>
          processAndAssertMatchedWorkIs(workLinksAv2, expectedMatchedWorkAv2)

          // Work V1 is sent but not matched
          val workLinksAv1 =
            createWorkLinksWith(
              id = identifierA,
              version = 1
            )

          sendWork(workLinksAv1, retriever, queue)
          eventually {
            noMessagesAreWaitingIn(queue)
            noMessagesAreWaitingIn(dlq)

            messageSender
              .getMessages[MatcherResult]
              .last shouldBe expectedMatchedWorkAv2
          }
        }
    }
  }

  it("does not match an existing version with different information") {
    val workLinksAv2 = createWorkLinksWith(
      id = identifierA,
      version = 2
    )

    val expectedMatchedWorkAv2 = MatcherResult(
      Set(
        MatchedIdentifiers(
          identifiers =
            Set(WorkIdentifier(identifierA.canonicalId, version = 2))
        )
      )
    )

    implicit val retriever: MemoryRetriever[WorkLinks] =
      new MemoryRetriever[WorkLinks]()
    implicit val messageSender: MemoryMessageSender = new MemoryMessageSender()

    withLocalSqsQueuePair() {
      case QueuePair(queue, dlq) =>
        implicit val q: SQS.Queue = queue

        withWorkerService(retriever, queue, messageSender) { _ =>
          processAndAssertMatchedWorkIs(workLinksAv2, expectedMatchedWorkAv2)

          // Work V1 is sent but not matched
          val differentWorkLinksAv2 =
            createWorkLinksWith(
              id = identifierA,
              version = 2,
              referencedIds = Set(identifierB)
            )

          sendWork(differentWorkLinksAv2, retriever, queue)
          eventually {
            assertQueueEmpty(queue)
            assertQueueHasSize(dlq, 1)
          }
        }
    }
  }

  private def processAndAssertMatchedWorkIs(links: WorkLinks,
                                            expectedResult: MatcherResult)(
    implicit
    retriever: MemoryRetriever[WorkLinks],
    queue: SQS.Queue,
    messageSender: MemoryMessageSender): Assertion = {
    sendWork(links, retriever, queue)
    eventually {
      messageSender.getMessages[MatcherResult].last shouldBe expectedResult
    }
  }
}
