package uk.ac.wellcome.platform.matcher

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.matcher.{
  MatchedIdentifiers,
  MatcherResult,
  WorkIdentifier,
  WorkNode
}
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.platform.matcher.fixtures.MatcherFixtures
import uk.ac.wellcome.storage.{Identified, Version}

class MatcherFeatureTest
    extends AnyFunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with MatcherFixtures
    with WorksGenerators {

  it(
    "processes a message with a simple Work.Visible[Source] with no linked works") {
    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue() { queue =>
      withVHS { vhs =>
        withWorkerService(vhs, queue, messageSender) { _ =>
          val work = createSourceWork

          val expectedResult = MatcherResult(
            Set(
              MatchedIdentifiers(
                identifiers = Set(WorkIdentifier(work))
              )
            )
          )

          sendWork(work, vhs, queue)

          eventually {
            messageSender.getMessages[MatcherResult].distinct shouldBe Seq(
              expectedResult)
          }
        }
      }
    }
  }

  it("skips a message if the graph store already has a newer version") {
    val messageSender = new MemoryMessageSender()

    withLocalSqsQueuePair() {
      case QueuePair(queue, dlq) =>
        withWorkGraphTable { graphTable =>
          withVHS { vhs =>
            withWorkerService(vhs, queue, messageSender, graphTable) { _ =>
              val existingWorkVersion = 2
              val updatedWorkVersion = 1

              val workAv1 = createSourceWorkWith(
                version = updatedWorkVersion
              )

              val existingWorkAv2 = WorkNode(
                id = workAv1.sourceIdentifier.toString,
                version = Some(existingWorkVersion),
                linkedIds = Nil,
                componentId = workAv1.sourceIdentifier.toString
              )
              put(dynamoClient, graphTable.name)(existingWorkAv2)

              sendWork(workAv1, vhs, queue)

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

  it("skips a message if VHS already has a newer version") {
    val messageSender = new MemoryMessageSender()

    withLocalSqsQueuePair() {
      case QueuePair(queue, dlq) =>
        withWorkGraphTable { graphTable =>
          withVHS { vhs: VHS =>
            withWorkerService(vhs, queue, messageSender, graphTable) { _ =>
              val workv2 = createSourceWorkWith(version = 2)

              val key = vhs.put(
                Version(workv2.sourceIdentifier.toString, workv2.version))(
                workv2) match {
                case Left(err) =>
                  throw new Exception(s"Failed storing work in VHS: $err")
                case Right(Identified(key, _)) => key
              }
              sendNotificationToSQS(queue, Version(key.id, 1))

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
}
