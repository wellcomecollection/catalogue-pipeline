package uk.ac.wellcome.platform.matcher

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.bigmessaging.EmptyMetadata
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.matcher.{
  MatchedIdentifiers,
  MatcherResult,
  WorkIdentifier,
  WorkNode
}
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.matcher.fixtures.MatcherFixtures
import uk.ac.wellcome.storage.store.HybridStoreEntry
import uk.ac.wellcome.storage.{Identified, Version}

class MatcherFeatureTest
    extends AnyFunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with MatcherFixtures
    with WorksGenerators {

  it("processes a message with a simple UnidentifiedWork with no linked works") {
    withLocalSnsTopic { topic =>
      withLocalSqsQueue { queue =>
        withVHS { vhs =>
          withWorkerService(vhs, queue, topic) { _ =>
            val work = createUnidentifiedWork

            sendWork(work, vhs, queue)

            eventually {
              val snsMessages = listMessagesReceivedFromSNS(topic)
              snsMessages.size should be >= 1

              snsMessages.map { snsMessage =>
                val identifiersList =
                  fromJson[MatcherResult](snsMessage.message).get

                identifiersList shouldBe
                  MatcherResult(
                    Set(MatchedIdentifiers(
                      Set(WorkIdentifier(work))
                    )))
              }
            }
          }
        }
      }
    }
  }

  it(
    "does not process a message if the work version is older than that already stored in the graph store") {
    withLocalSnsTopic { topic =>
      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          withWorkGraphTable { graphTable =>
            withVHS { vhs =>
              withWorkerService(vhs, queue, topic, graphTable) { _ =>
                val existingWorkVersion = 2
                val updatedWorkVersion = 1

                val workAv1 = createUnidentifiedWorkWith(
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
                listMessagesReceivedFromSNS(topic).size shouldBe 0
              }
            }
          }
      }
    }
  }

  it(
    "does not process a message if the work version is older than that already stored in vhs") {
    withLocalSnsTopic { topic =>
      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          withWorkGraphTable { graphTable =>
            withVHS { vhs: VHS =>
              withWorkerService(vhs, queue, topic, graphTable) { _ =>
                val workv2 = createUnidentifiedWorkWith(version = 2)

                val entry =
                  HybridStoreEntry[TransformedBaseWork, EmptyMetadata](
                    workv2,
                    EmptyMetadata())
                val key = vhs.put(
                  Version(workv2.sourceIdentifier.toString, workv2.version))(
                  entry) match {
                  case Left(err) =>
                    throw new Exception(s"Failed storing work in VHS: $err")
                  case Right(Identified(key, _)) => key
                }
                sendNotificationToSQS(queue, Version(key.id, 1))

                eventually {
                  assertQueueEmpty(queue)
                  assertQueueEmpty(dlq)
                }
                listMessagesReceivedFromSNS(topic).size shouldBe 0
              }
            }
          }
      }
    }
  }
}
