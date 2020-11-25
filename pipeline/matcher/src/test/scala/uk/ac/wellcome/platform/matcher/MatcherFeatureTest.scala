package uk.ac.wellcome.platform.matcher

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.matcher.{
  MatchedIdentifiers,
  MatcherResult,
  WorkIdentifier,
  WorkNode
}
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.models.work.internal.Work
import uk.ac.wellcome.models.work.internal.WorkState.Source
import uk.ac.wellcome.pipeline_storage.MemoryRetriever
import uk.ac.wellcome.platform.matcher.fixtures.MatcherFixtures

class MatcherFeatureTest
    extends AnyFunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with MatcherFixtures
    with WorkGenerators {

  it(
    "processes a message with a simple Work.Visible[Source] with no linked works") {
    val messageSender = new MemoryMessageSender()

    val retriever: MemoryRetriever[Work[Source]] =
      new MemoryRetriever[Work[Source]](
        index = Map[String, Work[Source]]()
      )

    withLocalSqsQueue() { queue =>
      withWorkerService(retriever, queue, messageSender) { _ =>
        val work = sourceWork()

        val expectedResult = MatcherResult(
          Set(
            MatchedIdentifiers(
              identifiers = Set(WorkIdentifier(work))
            )
          )
        )

        sendWork(work, retriever, queue)

        eventually {
          messageSender.getMessages[MatcherResult].distinct shouldBe Seq(
            expectedResult)
        }
      }
    }
  }

  it("skips a message if the graph store already has a newer version") {
    val messageSender = new MemoryMessageSender()

    val retriever: MemoryRetriever[Work[Source]] =
      new MemoryRetriever[Work[Source]](
        index = Map[String, Work[Source]]()
      )

    withLocalSqsQueuePair() {
      case QueuePair(queue, dlq) =>
        withWorkGraphTable { graphTable =>
          withWorkerService(retriever, queue, messageSender, graphTable) { _ =>
            val existingWorkVersion = 2
            val updatedWorkVersion = 1

            val workAv1 = sourceWork().withVersion(updatedWorkVersion)

            val existingWorkAv2 = WorkNode(
              id = workAv1.sourceIdentifier.toString,
              version = Some(existingWorkVersion),
              linkedIds = Nil,
              componentId = workAv1.sourceIdentifier.toString
            )
            put(dynamoClient, graphTable.name)(existingWorkAv2)

            sendWork(workAv1, retriever, queue)

            eventually {
              noMessagesAreWaitingIn(queue)
              noMessagesAreWaitingIn(dlq)
            }

            messageSender.messages shouldBe empty
          }
        }
    }
  }
}
