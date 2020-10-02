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
import uk.ac.wellcome.models.work.generators.SierraWorkGenerators
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.matcher.fixtures.MatcherFixtures
import uk.ac.wellcome.models.Implicits._
import WorkState.Source

class MatcherWorkerServiceTest
    extends AnyFunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with MatcherFixtures
    with SierraWorkGenerators {

  private val identifierA = createSierraSystemSourceIdentifierWith(value = "A")
  private val identifierB = createSierraSystemSourceIdentifierWith(value = "B")
  private val identifierC = createSierraSystemSourceIdentifierWith(value = "C")

  it("creates a work without identifiers") {
    // Work Av1 created without any matched works
    val updatedWork = sierraSourceWork()
    val expectedMatchedWorks =
      MatcherResult(
        Set(
          MatchedIdentifiers(identifiers = Set(WorkIdentifier(updatedWork)))
        )
      )

    implicit val messageSender: MemoryMessageSender = new MemoryMessageSender()

    withLocalSqsQueue() { implicit queue =>
      withVHS { implicit vhs =>
        withWorkerService(vhs, queue, messageSender) { _ =>
          processAndAssertMatchedWorkIs(
            updatedWork,
            expectedResult = expectedMatchedWorks)
        }
      }
    }
  }

  it(
    "sends an invisible work as a single matched result with no other matched identifiers") {
    val invisibleWork = sourceWork().invisible()
    val expectedMatchedWorks =
      MatcherResult(
        Set(
          MatchedIdentifiers(identifiers = Set(WorkIdentifier(invisibleWork)))
        )
      )

    implicit val messageSender: MemoryMessageSender = new MemoryMessageSender()

    withLocalSqsQueue() { implicit queue =>
      withVHS { implicit vhs =>
        withWorkerService(vhs, queue, messageSender) { _ =>
          processAndAssertMatchedWorkIs(
            invisibleWork,
            expectedResult = expectedMatchedWorks)
        }
      }
    }
  }

  it(
    "work A with one link to B and no existing works returns a single matched work") {
    // Work Av1
    val workAv1 =
      sourceWork(sourceIdentifier = identifierA)
        .withVersion(1)
        .mergeCandidates(List(MergeCandidate(identifierB)))

    // Work Av1 matched to B (before B exists hence version is None)
    // need to match to works that do not exist to support
    // bi-directionally matched works without deadlocking (A->B, B->A)
    val expectedMatchedWorks = MatcherResult(
      Set(
        MatchedIdentifiers(
          identifiers = Set(
            WorkIdentifier("sierra-system-number/A", version = Some(1)),
            WorkIdentifier("sierra-system-number/B", version = None)
          )
        )
      )
    )

    implicit val messageSender: MemoryMessageSender = new MemoryMessageSender()

    withLocalSqsQueue() { implicit queue =>
      withVHS { implicit vhs =>
        withWorkerService(vhs, queue, messageSender) { _ =>
          processAndAssertMatchedWorkIs(
            workAv1,
            expectedResult = expectedMatchedWorks)
        }
      }
    }
  }

  it(
    "matches a work with one link then matches the combined work to a new work") {
    // Work Av1
    val workAv1 = sourceWork(sourceIdentifier = identifierA).withVersion(1)

    val expectedMatchedWorksAv1 = MatcherResult(
      Set(
        MatchedIdentifiers(
          identifiers =
            Set(WorkIdentifier("sierra-system-number/A", version = 1))
        )
      )
    )

    // Work Bv1
    val workBv1 = sourceWork(sourceIdentifier = identifierB).withVersion(1)

    val expectedMatchedWorksBv1 = MatcherResult(
      Set(
        MatchedIdentifiers(
          identifiers =
            Set(WorkIdentifier("sierra-system-number/B", version = 1))
        )
      )
    )

    // Work Av1 matched to B
    val workAv2 = sourceWork(sourceIdentifier = identifierA)
      .withVersion(2)
      .mergeCandidates(List(MergeCandidate(identifierB)))

    val expectedMatchedWorksAv2 = MatcherResult(
      Set(
        MatchedIdentifiers(
          identifiers = Set(
            WorkIdentifier("sierra-system-number/A", version = 2),
            WorkIdentifier("sierra-system-number/B", version = 1)
          )
        )
      )
    )

    // Work Cv1
    val workCv1 = sourceWork(sourceIdentifier = identifierC)
      .withVersion(1)

    val expectedMatcherWorksCv1 =
      MatcherResult(
        Set(
          MatchedIdentifiers(
            identifiers =
              Set(WorkIdentifier("sierra-system-number/C", version = 1))
          )
        )
      )

    // Work Bv2 matched to C
    val workBv2 = sourceWork(sourceIdentifier = identifierB)
      .withVersion(2)
      .mergeCandidates(List(MergeCandidate(identifierC)))

    val expectedMatchedWorksBv2 =
      MatcherResult(
        Set(
          MatchedIdentifiers(
            identifiers = Set(
              WorkIdentifier("sierra-system-number/A", version = 2),
              WorkIdentifier("sierra-system-number/B", version = 2),
              WorkIdentifier("sierra-system-number/C", version = 1)
            )
          )
        )
      )

    implicit val messageSender: MemoryMessageSender = new MemoryMessageSender()

    withLocalSqsQueue() { implicit queue =>
      withVHS { implicit vhs =>
        withWorkerService(vhs, queue, messageSender) { _ =>
          processAndAssertMatchedWorkIs(workAv1, expectedMatchedWorksAv1)
          processAndAssertMatchedWorkIs(workBv1, expectedMatchedWorksBv1)
          processAndAssertMatchedWorkIs(workAv2, expectedMatchedWorksAv2)
          processAndAssertMatchedWorkIs(workCv1, expectedMatcherWorksCv1)
          processAndAssertMatchedWorkIs(workBv2, expectedMatchedWorksBv2)
        }
      }
    }
  }

  it("breaks matched works into individual works") {
    // Work Av1
    val workAv1 = sourceWork(sourceIdentifier = identifierA).withVersion(1)

    val expectedMatchedWorksAv1 = MatcherResult(
      Set(
        MatchedIdentifiers(
          identifiers =
            Set(WorkIdentifier("sierra-system-number/A", version = 1))
        )
      )
    )

    // Work Bv1
    val workBv1 = sourceWork(sourceIdentifier = identifierB).withVersion(1)

    val expectedMatchedWorksBv1 = MatcherResult(
      Set(
        MatchedIdentifiers(
          identifiers =
            Set(WorkIdentifier("sierra-system-number/B", version = 1))
        )
      )
    )

    // Match Work A to Work B
    val workAv2MatchedToB =
      sourceWork(sourceIdentifier = identifierA)
        .withVersion(2)
        .mergeCandidates(List(MergeCandidate(identifierB)))

    val expectedMatchedWorksAv2MatchedToB =
      MatcherResult(
        Set(
          MatchedIdentifiers(
            identifiers = Set(
              WorkIdentifier("sierra-system-number/A", version = 2),
              WorkIdentifier("sierra-system-number/B", version = 1)
            )
          )
        )
      )

    // A no longer matches B
    val workAv3WithNoMatchingWorks =
      sourceWork(sourceIdentifier = identifierA).withVersion(3)

    val expectedMatchedWorksAv3 =
      MatcherResult(
        Set(
          MatchedIdentifiers(
            identifiers =
              Set(WorkIdentifier("sierra-system-number/A", version = 3))
          ),
          MatchedIdentifiers(
            identifiers =
              Set(WorkIdentifier("sierra-system-number/B", version = 1))
          )
        )
      )

    implicit val messageSender: MemoryMessageSender = new MemoryMessageSender()

    withLocalSqsQueue() { implicit queue =>
      withVHS { implicit vhs =>
        withWorkerService(vhs, queue, messageSender) { _ =>
          processAndAssertMatchedWorkIs(workAv1, expectedMatchedWorksAv1)
          processAndAssertMatchedWorkIs(workBv1, expectedMatchedWorksBv1)
          processAndAssertMatchedWorkIs(
            workAv2MatchedToB,
            expectedMatchedWorksAv2MatchedToB)
          processAndAssertMatchedWorkIs(
            workAv3WithNoMatchingWorks,
            expectedMatchedWorksAv3)
        }
      }
    }
  }

  it("does not match a lower version") {
    val workAv2 = sourceWork(sourceIdentifier = identifierA).withVersion(2)

    val expectedMatchedWorkAv2 = MatcherResult(
      Set(
        MatchedIdentifiers(
          identifiers =
            Set(WorkIdentifier("sierra-system-number/A", version = 2))
        )
      )
    )

    implicit val messageSender: MemoryMessageSender = new MemoryMessageSender()

    withLocalSqsQueuePair() {
      case QueuePair(queue, dlq) =>
        implicit val q: SQS.Queue = queue

        withVHS { implicit vhs =>
          withWorkerService(vhs, queue, messageSender) { _ =>
            processAndAssertMatchedWorkIs(workAv2, expectedMatchedWorkAv2)

            // Work V1 is sent but not matched
            val workAv1 =
              sourceWork(sourceIdentifier = identifierA).withVersion(1)

            sendWork(workAv1, vhs, queue)
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
  }

  it("does not match an existing version with different information") {
    val workAv2 = sourceWork(sourceIdentifier = identifierA).withVersion(2)

    val expectedMatchedWorkAv2 = MatcherResult(
      Set(
        MatchedIdentifiers(
          identifiers =
            Set(WorkIdentifier("sierra-system-number/A", version = 2))
        )
      )
    )

    implicit val messageSender: MemoryMessageSender = new MemoryMessageSender()

    withLocalSqsQueuePair() {
      case QueuePair(queue, dlq) =>
        implicit val q: SQS.Queue = queue

        withVHS { implicit vhs =>
          withWorkerService(vhs, queue, messageSender) { _ =>
            processAndAssertMatchedWorkIs(workAv2, expectedMatchedWorkAv2)

            // Work V1 is sent but not matched
            val differentWorkAv2 =
              sourceWork(sourceIdentifier = identifierA)
                .withVersion(2)
                .mergeCandidates(List(MergeCandidate(identifierB)))

            sendWork(differentWorkAv2, vhs, queue)
            eventually {
              assertQueueEmpty(queue)
              assertQueueHasSize(dlq, 1)
            }
          }
        }
    }
  }

  private def processAndAssertMatchedWorkIs(workToMatch: Work[Source],
                                            expectedResult: MatcherResult)(
    implicit
    vhs: VHS,
    queue: SQS.Queue,
    messageSender: MemoryMessageSender): Assertion = {
    sendWork(workToMatch, vhs, queue)
    eventually {
      messageSender.getMessages[MatcherResult].last shouldBe expectedResult
    }
  }
}
