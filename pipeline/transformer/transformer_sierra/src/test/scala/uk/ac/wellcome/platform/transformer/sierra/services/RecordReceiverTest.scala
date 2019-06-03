package uk.ac.wellcome.platform.transformer.sierra.services

import io.circe.ParsingFailure
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.Messaging
import uk.ac.wellcome.messaging.message.MessageNotification
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.transformable.SierraTransformable
import uk.ac.wellcome.models.transformable.SierraTransformable._
import uk.ac.wellcome.models.transformable.sierra.test.utils.SierraGenerators
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal.{TransformedBaseWork, UnidentifiedWork}
import uk.ac.wellcome.platform.transformer.sierra.fixtures.RecordReceiverFixture
import uk.ac.wellcome.storage.streaming.CodecInstances._

import scala.util.{Failure, Success, Try}

class RecordReceiverTest
    extends FunSpec
    with Matchers
    with Messaging
    with RecordReceiverFixture
    with SierraGenerators
    with WorksGenerators {

  case class TestException(message: String) extends Exception(message)

  def transformToWork(transformable: SierraTransformable, version: Int) =
    Try(createUnidentifiedWorkWith(version = version))
  def failingTransformToWork(transformable: SierraTransformable, version: Int) =
    Try(throw TestException("BOOOM!"))

  it("receives a message and sends it to SNS client") {
    val store = new SierraTransformableStore()
    val workMessageSender = new WorkSender()

    val recordReceiver = createRecordReceiver(store, workMessageSender)

    val message = createSierraNotificationMessageWith(store)

    recordReceiver.receiveMessage(message, transformToWork) shouldBe a[Success[_]]

    val works = workMessageSender.getMessages[TransformedBaseWork]
    works.size should be >= 1

    works.map { work =>
      work shouldBe a[UnidentifiedWork]
    }
  }

  it("receives a message and adds the version to the transformed work") {
    val version = 5

    val store = new SierraTransformableStore()
    val workMessageSender = new WorkSender()

    val recordReceiver = createRecordReceiver(store, workMessageSender)

    val message = createSierraNotificationMessageWith(store, version = version)

    recordReceiver.receiveMessage(message, transformToWork) shouldBe a[Success[_]]

    val works = workMessageSender.getMessages[TransformedBaseWork]
    works.size should be >= 1

    works.map { actualWork =>
      actualWork shouldBe a[UnidentifiedWork]
      val unidentifiedWork = actualWork.asInstanceOf[UnidentifiedWork]
      unidentifiedWork.version shouldBe version
    }
  }

  it("fails if it's unable to parse the SQS message") {
    val recordReceiver = createRecordReceiver()

    val result = recordReceiver.receiveMessage(NotificationMessage("not a JSON string"), transformToWork)
    result shouldBe a[Failure[_]]
    result.failed.get shouldBe a[ParsingFailure]
  }

  it("fails if it's unable to perform a transformation") {
    val store = new SierraTransformableStore()

    val recordReceiver = createRecordReceiver(store)

    val message = createSierraNotificationMessageWith(store)

    val result = recordReceiver.receiveMessage(message, transformToWork)
    result shouldBe a[Failure[_]]
    result.failed.get shouldBe a[TestException]
  }

  it("fails if it's unable to publish the work") {
    val store = new SierraTransformableStore()

    val exception = new Throwable("BOOM!")
    val brokenMessageSender = new WorkSender() {
      override def sendT(t: TransformedBaseWork): Try[MessageNotification] =
        Failure(exception)
    }

    val recordReceiver = createRecordReceiver(store, brokenMessageSender)

    val message = createSierraNotificationMessageWith(store)

    val result = recordReceiver.receiveMessage(message, transformToWork)

    result.failed.get shouldBe exception
  }
}
