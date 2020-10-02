package uk.ac.wellcome.platform.transformer.miro.services

import scala.util.{Failure, Try}

import io.circe.Encoder
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.miro.exceptions.MiroTransformerException
import uk.ac.wellcome.platform.transformer.miro.fixtures.MiroVHSRecordReceiverFixture
import uk.ac.wellcome.platform.transformer.miro.generators.MiroRecordGenerators
import uk.ac.wellcome.platform.transformer.miro.models.MiroMetadata
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import WorkState.Source

class MiroVHSRecordReceiverTest
    extends AnyFunSpec
    with Matchers
    with MiroVHSRecordReceiverFixture
    with ScalaFutures
    with IntegrationPatience
    with MiroRecordGenerators
    with WorkGenerators {

  case class TestException(message: String) extends Exception(message)

  def transformToWork(miroRecord: MiroRecord,
                      metadata: MiroMetadata,
                      version: Int) =
    Try(sourceWork(version = version))

  def failingTransformToWork(miroRecord: MiroRecord,
                             metadata: MiroMetadata,
                             version: Int) =
    Try(throw TestException("BOOOM!"))

  it("receives a message and sends it to SNS client") {
    val message = createHybridRecordNotification

    val messageSender = new MemoryMessageSender()
    val recordReceiver = createRecordReceiverWith(messageSender)

    val future = recordReceiver.receiveMessage(message, transformToWork)

    whenReady(future) { _ =>
      val works = messageSender.getMessages[Work[Source]]
      works.size should be >= 1

      works.map { work =>
        work shouldBe a[Work.Visible[_]]
      }
    }
  }

  it("receives a message and adds the version to the transformed work") {
    val version = 5

    val message = createHybridRecordNotificationWith(version = version)

    val messageSender = new MemoryMessageSender()
    val recordReceiver = createRecordReceiverWith(messageSender)

    val future = recordReceiver.receiveMessage(message, transformToWork)

    whenReady(future) { _ =>
      val works = messageSender.getMessages[Work[Source]]
      works.size should be >= 1

      works.map { actualWork =>
        actualWork shouldBe a[Work.Visible[_]]
        val unidentifiedWork =
          actualWork.asInstanceOf[Work.Visible[Source]]
        unidentifiedWork.version shouldBe version
      }
    }
  }

  // It's not possible to store a record without metadata with the HybridStore
  // used in these tests
  ignore("returns a failed future if there's no MiroMetadata") {
    val incompleteMessage = createHybridRecordNotification

    val messageSender = new MemoryMessageSender()
    val recordReceiver = createRecordReceiverWith(messageSender)

    val future =
      recordReceiver.receiveMessage(incompleteMessage, transformToWork)

    whenReady(future.failed) {
      _ shouldBe a[MiroTransformerException]
    }
  }

  it("returns a failed future if there's no HybridRecord") {
    val incompleteMessage = createNotificationMessageWith(
      message = MiroMetadata(isClearedForCatalogueAPI = false)
    )

    val messageSender = new MemoryMessageSender()
    val recordReceiver = createRecordReceiverWith(messageSender)

    val future =
      recordReceiver.receiveMessage(incompleteMessage, transformToWork)

    whenReady(future.failed) {
      _ shouldBe a[MiroTransformerException]
    }
  }

  it("fails if it's unable to perform a transformation") {
    val message = createHybridRecordNotification

    val messageSender = new MemoryMessageSender()
    val recordReceiver = createRecordReceiverWith(messageSender)

    val future = recordReceiver.receiveMessage(message, failingTransformToWork)

    whenReady(future.failed) {
      _ shouldBe a[TestException]
    }
  }

  it("fails if it's unable to publish the work") {
    val brokenSender = new MemoryMessageSender() {
      override def sendT[T](t: T)(implicit encoder: Encoder[T]): Try[Unit] =
        Failure(new Throwable("BOOM!"))
    }

    val message = createHybridRecordNotification

    val recordReceiver = createRecordReceiverWith(brokenSender)

    val future = recordReceiver.receiveMessage(message, transformToWork)

    whenReady(future.failed) {
      _.getMessage should include("BOOM!")
    }
  }
}
