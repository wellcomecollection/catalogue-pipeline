package weco.pipeline.matcher

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.messaging.fixtures.SQS.QueuePair
import weco.messaging.memory.MemoryMessageSender
import weco.fixtures.TimeAssertions
import weco.json.JsonUtil._
import weco.pipeline.matcher.fixtures.MatcherFixtures
import weco.pipeline.matcher.generators.WorkStubGenerators
import weco.pipeline.matcher.models.{
  MatchedIdentifiers,
  MatcherResult,
  WorkIdentifier,
  WorkStub,
  WorkNode
}
import weco.pipeline_storage.memory.MemoryRetriever

import scala.concurrent.ExecutionContext.Implicits.global

class MatcherFeatureTest
    extends AnyFunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with MatcherFixtures
    with WorkStubGenerators
    with TimeAssertions {

  it("processes a message with a single work with no linked works") {
    implicit val retriever: MemoryRetriever[WorkStub] =
      new MemoryRetriever[WorkStub]()
    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue() { queue =>
      withWorkerService(retriever, queue, messageSender) { _ =>
        val work = createWorkStubWith(referencedIds = Set.empty)

        val expectedWorks =
          Set(
            MatchedIdentifiers(
              identifiers =
                Set(WorkIdentifier(work.id, version = work.version))
            )
          )

        sendWork(work, retriever, queue)

        eventually {
          messageSender.messages should have size 1

          val result = messageSender.getMessages[MatcherResult].head
          result.works shouldBe expectedWorks
          assertRecent(result.createdTime)
        }
      }
    }
  }

  it("skips a message if the graph store already has a newer version") {
    implicit val retriever: MemoryRetriever[WorkStub] =
      new MemoryRetriever[WorkStub]()
    val messageSender = new MemoryMessageSender()

    withLocalSqsQueuePair() {
      case QueuePair(queue, dlq) =>
        withWorkGraphTable { graphTable =>
          withWorkerService(retriever, queue, messageSender, graphTable) { _ =>
            val existingWorkVersion = 2
            val updatedWorkVersion = 1

            val worksV1 = createWorkStubWith(version = updatedWorkVersion)

            val nodeV2 = WorkNode(
              id = worksV1.id,
              version = Some(existingWorkVersion),
              linkedIds = Nil,
              componentId = ciHash(worksV1.id)
            )
            put(dynamoClient, graphTable.name)(nodeV2)

            sendWork(worksV1, retriever, queue)

            eventually {
              assertQueueEmpty(queue)
              assertQueueEmpty(dlq)
            }

            messageSender.messages shouldBe empty
          }
        }
    }
  }
}
