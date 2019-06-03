package uk.ac.wellcome.platform.transformer.miro.services

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.message.MessageNotification
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal.{TransformedBaseWork, UnidentifiedWork}
import uk.ac.wellcome.platform.transformer.miro.fixtures.MiroVHSRecordReceiverFixture
import uk.ac.wellcome.platform.transformer.miro.generators.MiroRecordGenerators
import uk.ac.wellcome.platform.transformer.miro.models.MiroMetadata
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord
import uk.ac.wellcome.storage.streaming.CodecInstances._

import scala.util.{Failure, Success, Try}

class MiroVHSRecordReceiverTest
    extends FunSpec
    with Matchers
    with MiroVHSRecordReceiverFixture
    with MiroRecordGenerators
    with WorksGenerators {

  case class TestException(message: String) extends Exception(message)

  def transformToWork(miroRecord: MiroRecord,
                      metadata: MiroMetadata,
                      version: Int) =
    Try(createUnidentifiedWorkWith(version = version))

  def failingTransformToWork(miroRecord: MiroRecord,
                             metadata: MiroMetadata,
                             version: Int) =
    Try(throw TestException("BOOOM!"))

  it("receives a message and sends it to SNS client") {
    val store = new MiroRecordStore()
    val workMessageSender = new WorkSender()

    val recordReceiver = createRecordReceiver(store, workMessageSender)

    val message = createMiroVHSRecordNotificationMessageWith(store)

    recordReceiver.receiveMessage(message, transformToWork) shouldBe a[Success[_]]

    val works = workMessageSender.getMessages[TransformedBaseWork]
    works.size should be >= 1

    works.map { work =>
      work shouldBe a[UnidentifiedWork]
    }
  }

  it("receives a message and adds the version to the transformed work") {
    val version = 5

    val store = new MiroRecordStore()
    val workMessageSender = new WorkSender()

    val recordReceiver = createRecordReceiver(store, workMessageSender)

    val message = createMiroVHSRecordNotificationMessageWith(store, version = version)

    recordReceiver.receiveMessage(message, transformToWork) shouldBe a[Success[_]]

    val works = workMessageSender.getMessages[TransformedBaseWork]
    works.size should be >= 1

    works.map { actualWork =>
      actualWork shouldBe a[UnidentifiedWork]
      val unidentifiedWork = actualWork.asInstanceOf[UnidentifiedWork]
      unidentifiedWork.version shouldBe version
    }
  }

  it("fails if it's unable to perform a transformation") {
    val store = new MiroRecordStore()

    val recordReceiver = createRecordReceiver(store)

    val message = createMiroVHSRecordNotificationMessageWith(store)

    val result = recordReceiver.receiveMessage(message, failingTransformToWork)

    result.failed.get shouldBe a[TestException]
  }

  it("fails if it's unable to publish the work") {
    val store = new MiroRecordStore()

    val exception = new Throwable("BOOM!")
    val brokenMessageSender = new WorkSender() {
      override def sendT(t: TransformedBaseWork): Try[MessageNotification] =
        Failure(exception)
    }

    val recordReceiver = createRecordReceiver(store, brokenMessageSender)

    val message = createMiroVHSRecordNotificationMessageWith(store)

    val result = recordReceiver.receiveMessage(message, transformToWork)

    result.failed.get shouldBe exception
  }
}
