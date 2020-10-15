package uk.ac.wellcome.platform.id_minter_works

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.sqs.model.QueueAttributeName

import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.platform.id_minter_works.fixtures.WorkerServiceFixture
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.models.Implicits._
import WorkState.{Denormalised, Identified}

class IdMinterFeatureTest
    extends AnyFunSpec
    with Matchers
    with IntegrationPatience
    with Eventually
    with WorkerServiceFixture
    with WorkGenerators {

  it("mints the same IDs where source identifiers match") {
    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue() { queue =>
      withIdentifiersDatabase { identifiersTableConfig =>
        val work: Work[Denormalised] = denormalisedWork()
        val index = createIndex(List(work))
        withWorkerService(messageSender, queue, identifiersTableConfig, index) {
          _ =>
            eventuallyTableExists(identifiersTableConfig)

            val messageCount = 5

            (1 to messageCount).foreach { _ =>
              sendNotificationToSQS(queue = queue, body = work.id)
            }

            eventually {
              val works = messageSender.getMessages[Work[Identified]]
              works.length shouldBe >=(messageCount)

              works.map(_.state.canonicalId).distinct should have size 1
              works.foreach { receivedWork =>
                receivedWork
                  .asInstanceOf[Work.Visible[Identified]]
                  .sourceIdentifier shouldBe work.sourceIdentifier
                receivedWork
                  .asInstanceOf[Work.Visible[Identified]]
                  .data
                  .title shouldBe work.data.title
              }
            }
        }
      }
    }
  }

  it("mints an identifier for a invisible work") {
    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue() { queue =>
      withIdentifiersDatabase { identifiersTableConfig =>
        val work: Work[Denormalised] = denormalisedWork().invisible()
        val index = createIndex(List(work))
        withWorkerService(messageSender, queue, identifiersTableConfig, index) {
          _ =>
            eventuallyTableExists(identifiersTableConfig)

            sendNotificationToSQS(queue = queue, body = work.id)

            eventually {
              val works = messageSender.getMessages[Work[Identified]]
              works.length shouldBe >=(1)

              val receivedWork = works.head
              val invisibleWork =
                receivedWork.asInstanceOf[Work.Invisible[Identified]]
              invisibleWork.sourceIdentifier shouldBe work.sourceIdentifier
              invisibleWork.state.canonicalId shouldNot be(empty)
            }
        }
      }
    }
  }

  it("mints an identifier for a redirected work") {
    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue() { queue =>
      withIdentifiersDatabase { identifiersTableConfig =>
        val work: Work[Denormalised] = denormalisedWork()
          .redirected(redirect = IdState.Identifiable(createSourceIdentifier))
        val index = createIndex(List(work))
        withWorkerService(messageSender, queue, identifiersTableConfig, index) {
          _ =>
            eventuallyTableExists(identifiersTableConfig)

            sendNotificationToSQS(queue = queue, body = work.id)

            eventually {
              val works = messageSender.getMessages[Work[Identified]]
              works.length shouldBe >=(1)

              val receivedWork = works.head
              val redirectedWork =
                receivedWork.asInstanceOf[Work.Redirected[Identified]]
              redirectedWork.sourceIdentifier shouldBe work.sourceIdentifier
              redirectedWork.state.canonicalId shouldNot be(empty)
              redirectedWork.redirect.canonicalId shouldNot be(empty)
            }
        }
      }
    }
  }

  it("continues if something fails processing a message") {
    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue() { queue =>
      withIdentifiersDatabase { identifiersTableConfig =>
        val work: Work[Denormalised] = denormalisedWork()
        val index = createIndex(List(work))
        withWorkerService(messageSender, queue, identifiersTableConfig, index) {
          _ =>
            sendInvalidJSONto(queue)

            sendNotificationToSQS(queue = queue, body = work.id)

            eventually {
              messageSender.messages should not be empty

              assertMessageIsNotDeleted(queue)
            }
        }
      }
    }
  }

  it("mints ID when using Elasticsearch storage backend") {
    val messageSender = new MemoryMessageSender()
    val work: Work[Denormalised] = denormalisedWork()

    withLocalSqsQueue() { queue =>
      withElasticStorageWorkerService(work, queue, messageSender) { workerService =>

        sendNotificationToSQS(queue = queue, body = work.id)

        eventually {
          val works = messageSender.getMessages[Work[Identified]]
          works.length shouldBe >=(1)

          val receivedWork = works.head.asInstanceOf[Work[Identified]]
          receivedWork.sourceIdentifier shouldBe work.sourceIdentifier
          receivedWork.state.canonicalId shouldNot be(empty)
        }
      }
    }
  }

  private def assertMessageIsNotDeleted(queue: Queue): Unit = {
    // After a message is read, it stays invisible for 1 second and then it gets sent again.
    // So we wait for longer than the visibility timeout and then we assert that it has become
    // invisible again, which means that the id_minter picked it up again,
    // and so it wasn't deleted as part of the first run.
    // TODO Write this test using dead letter queues once https://github.com/adamw/elasticmq/issues/69 is closed
    Thread.sleep(2000)

    getQueueAttribute(
      queue,
      attributeName =
        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE) shouldBe "1"
  }
}
