package uk.ac.wellcome.platform.matcher.services

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.{Assertion, FunSpec, Matchers}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.matcher.{
  MatchedIdentifiers,
  MatcherResult,
  WorkIdentifier
}
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.matcher.fixtures.MatcherFixtures

class MatcherWorkerServiceTest
    extends FunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with MatcherFixtures
    with WorksGenerators {

  private val identifierA = createSierraSystemSourceIdentifierWith(value = "A")
  private val identifierB = createSierraSystemSourceIdentifierWith(value = "B")
  private val identifierC = createSierraSystemSourceIdentifierWith(value = "C")

  it("creates a work without identifiers") {
    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue { queue =>
      withWorkerService(queue, messageSender) { _ =>
        // Work Av1 created without any matched works
        val updatedWork = createUnidentifiedSierraWork
        val expectedMatchedWorks =
          MatcherResult(
            Set(MatchedIdentifiers(Set(WorkIdentifier(updatedWork)))))

        processAndAssertMatchedWorkIs(
          updatedWork,
          expectedMatchedWorks,
          queue,
          messageSender)
      }
    }
  }

  it(
    "sends an invisible work as a single matched result with no other matched identifiers") {
    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue { queue =>
      withWorkerService(queue, messageSender) { _ =>
        val invisibleWork = createUnidentifiedInvisibleWork
        val expectedMatchedWorks =
          MatcherResult(
            Set(
              MatchedIdentifiers(
                Set(WorkIdentifier(invisibleWork))
              ))
          )

        processAndAssertMatchedWorkIs(
          workToMatch = invisibleWork,
          expectedMatchedWorks = expectedMatchedWorks,
          queue = queue,
          messageSender = messageSender
        )
      }
    }
  }

  it(
    "work A with one link to B and no existing works returns a single matched work") {
    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue { queue =>
      withWorkerService(queue, messageSender) { _ =>
        // Work Av1
        val workAv1 =
          createUnidentifiedWorkWith(
            sourceIdentifier = identifierA,
            mergeCandidates = List(MergeCandidate(identifierB)))
        // Work Av1 matched to B (before B exists hence version 0)
        // need to match to works that do not exist to support
        // bi-directionally matched works without deadlocking (A->B, B->A)
        val expectedMatchedWorks = MatcherResult(
          Set(
            MatchedIdentifiers(Set(
              WorkIdentifier("sierra-system-number/A", 1),
              WorkIdentifier("sierra-system-number/B", 0)))))

        processAndAssertMatchedWorkIs(
          workAv1,
          expectedMatchedWorks,
          queue,
          messageSender)
      }
    }
  }

  it(
    "matches a work with one link then matches the combined work to a new work") {
    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue { queue =>
      withWorkerService(queue, messageSender) { _ =>
        // Work Av1
        val workAv1 =
          createUnidentifiedWorkWith(sourceIdentifier = identifierA)

        val expectedMatchedWorks = MatcherResult(
          Set(
            MatchedIdentifiers(Set(
              WorkIdentifier("sierra-system-number/A", 1)
            ))))

        processAndAssertMatchedWorkIs(
          workAv1,
          expectedMatchedWorks,
          queue,
          messageSender)

        // Work Bv1
        val workBv1 =
          createUnidentifiedWorkWith(sourceIdentifier = identifierB)

        processAndAssertMatchedWorkIs(
          workBv1,
          MatcherResult(
            Set(MatchedIdentifiers(
              Set(WorkIdentifier("sierra-system-number/B", 1))))),
          queue,
          messageSender)

        // Work Av1 matched to B
        val workAv2 = createUnidentifiedWorkWith(
          sourceIdentifier = identifierA,
          version = 2,
          mergeCandidates = List(MergeCandidate(identifierB)))

        processAndAssertMatchedWorkIs(
          workAv2,
          MatcherResult(
            Set(
              MatchedIdentifiers(Set(
                WorkIdentifier("sierra-system-number/A", 2),
                WorkIdentifier("sierra-system-number/B", 1))))),
          queue,
          messageSender
        )

        // Work Cv1
        val workCv1 =
          createUnidentifiedWorkWith(sourceIdentifier = identifierC)

        processAndAssertMatchedWorkIs(
          workCv1,
          MatcherResult(
            Set(MatchedIdentifiers(
              Set(WorkIdentifier("sierra-system-number/C", 1))))),
          queue,
          messageSender)

        // Work Bv2 matched to C
        val workBv2 = createUnidentifiedWorkWith(
          sourceIdentifier = identifierB,
          version = 2,
          mergeCandidates = List(MergeCandidate(identifierC)))

        processAndAssertMatchedWorkIs(
          workBv2,
          MatcherResult(
            Set(
              MatchedIdentifiers(
                Set(
                  WorkIdentifier("sierra-system-number/A", 2),
                  WorkIdentifier("sierra-system-number/B", 2),
                  WorkIdentifier("sierra-system-number/C", 1))))),
          queue,
          messageSender
        )
      }
    }
  }

  it("breaks matched works into individual works") {
    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue { queue =>
      withWorkerService(queue, messageSender) { _ =>
        // Work Av1
        val workAv1 = createUnidentifiedWorkWith(
          sourceIdentifier = identifierA,
          version = 1)

        processAndAssertMatchedWorkIs(
          workAv1,
          MatcherResult(
            Set(MatchedIdentifiers(
              Set(WorkIdentifier("sierra-system-number/A", 1))))),
          queue,
          messageSender)

        // Work Bv1
        val workBv1 = createUnidentifiedWorkWith(
          sourceIdentifier = identifierB,
          version = 1)

        processAndAssertMatchedWorkIs(
          workBv1,
          MatcherResult(
            Set(MatchedIdentifiers(
              Set(WorkIdentifier("sierra-system-number/B", 1))))),
          queue,
          messageSender)

        // Match Work A to Work B
        val workAv2MatchedToB = createUnidentifiedWorkWith(
          sourceIdentifier = identifierA,
          version = 2,
          mergeCandidates = List(MergeCandidate(identifierB)))

        processAndAssertMatchedWorkIs(
          workAv2MatchedToB,
          MatcherResult(
            Set(
              MatchedIdentifiers(Set(
                WorkIdentifier("sierra-system-number/A", 2),
                WorkIdentifier("sierra-system-number/B", 1))))),
          queue,
          messageSender
        )

        // A no longer matches B
        val workAv3WithNoMatchingWorks = createUnidentifiedWorkWith(
          sourceIdentifier = identifierA,
          version = 3)

        processAndAssertMatchedWorkIs(
          workAv3WithNoMatchingWorks,
          MatcherResult(
            Set(
              MatchedIdentifiers(
                Set(WorkIdentifier("sierra-system-number/A", 3))),
              MatchedIdentifiers(
                Set(WorkIdentifier("sierra-system-number/B", 1))))),
          queue,
          messageSender
        )
      }
    }
  }

  it("does not match a lower version") {
    val messageSender = new MemoryMessageSender()

    withLocalSqsQueueAndDlq { queuePair =>
      withWorkerService(queuePair.queue, messageSender) { _ =>
        // process Work V2
        val workAv2 = createUnidentifiedWorkWith(
          sourceIdentifier = identifierA,
          version = 2
        )

        val expectedMatchedWorkAv2 = MatcherResult(Set(
          MatchedIdentifiers(Set(WorkIdentifier("sierra-system-number/A", 2)))))

        processAndAssertMatchedWorkIs(
          workAv2,
          expectedMatchedWorkAv2,
          queuePair.queue,
          messageSender)

        // Work V1 is sent but not matched
        val workAv1 = createUnidentifiedWorkWith(
          sourceIdentifier = identifierA,
          version = 1)

        sendMessage[TransformedBaseWork](queue = queuePair.queue, workAv1)
        eventually {
          noMessagesAreWaitingIn(queuePair.queue)
          noMessagesAreWaitingIn(queuePair.dlq)
          assertLastMatchedResultIs(
            messageSender = messageSender,
            expectedMatcherResult = expectedMatchedWorkAv2
          )
        }
      }
    }
  }

  it("does not match an existing version with different information") {
    val messageSender = new MemoryMessageSender()

    withLocalSqsQueueAndDlq {
      case QueuePair(queue, dlq) =>
        withWorkerService(queue, messageSender) { _ =>
          val workAv2 = createUnidentifiedWorkWith(
            sourceIdentifier = identifierA,
            version = 2
          )

          val expectedMatchedWorkAv2 = MatcherResult(
            Set(MatchedIdentifiers(
              Set(WorkIdentifier("sierra-system-number/A", 2)))))

          processAndAssertMatchedWorkIs(
            workAv2,
            expectedMatchedWorkAv2,
            queue,
            messageSender)

          // Work V1 is sent but not matched
          val differentWorkAv2 = createUnidentifiedWorkWith(
            sourceIdentifier = identifierA,
            mergeCandidates = List(MergeCandidate(identifierB)),
            version = 2)

          sendMessage[TransformedBaseWork](queue = queue, differentWorkAv2)
          eventually {
            assertQueueEmpty(queue)
            assertQueueHasSize(dlq, 1)
          }
        }
    }
  }

  private def processAndAssertMatchedWorkIs(
    workToMatch: TransformedBaseWork,
    expectedMatchedWorks: MatcherResult,
    queue: SQS.Queue,
    messageSender: MemoryMessageSender): Any = {
    sendMessage(queue = queue, workToMatch)
    eventually {
      assertLastMatchedResultIs(
        messageSender = messageSender,
        expectedMatcherResult = expectedMatchedWorks
      )
    }
  }

  private def assertLastMatchedResultIs(
    messageSender: MemoryMessageSender,
    expectedMatcherResult: MatcherResult): Assertion = {
    val results = messageSender.getMessages[MatcherResult]

    results.size should be >= 1
    results.last shouldBe expectedMatcherResult
  }
}
