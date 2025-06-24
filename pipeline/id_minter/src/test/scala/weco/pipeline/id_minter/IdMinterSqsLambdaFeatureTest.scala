package weco.pipeline.id_minter

import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{CanonicalId, IdState}
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.lambda._
import weco.lambda.helpers.LambdaFixtures
import weco.messaging.memory.MemoryMessageSender
import weco.pipeline.id_minter.config.models.IdentifiersTableConfig
import weco.pipeline.id_minter.utils.IdMinterSqsLambdaTestHelpers

import scala.collection.mutable

class IdMinterSqsLambdaFeatureTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with IdMinterSqsLambdaTestHelpers
    with IntegrationPatience
    with Eventually
    with WorkGenerators
    with LambdaFixtures {

  it("mints the same IDs where source identifiers match") {
    val work = sourceWork()
    val inputIndex = createIndex(List(work))
    val outputIndex = mutable.Map.empty[String, Work[Identified]]
    val msgSender = new MemoryMessageSender()

    withIdentifiersTable {
      identifiersTableConfig: IdentifiersTableConfig =>
        withIdMinterSQSLambda(
          identifiersTableConfig = identifiersTableConfig,
          msgSender = Some(msgSender),
          mergedIndex = inputIndex,
          identifiedIndex = outputIndex
        ) {
          idMinterSqsLambda =>
            eventuallyTableExists(identifiersTableConfig)

            val messageCount = 5
            val messages = (1 to messageCount).map {
              _ => SQSTestLambdaMessage(message = work.id)
            }

            whenReady(idMinterSqsLambda.processMessages(messages)) {
              results =>
                val sentIds = msgSender.messages.map(_.body)

                val identifiedWork = outputIndex(sentIds.head)
                identifiedWork.sourceIdentifier shouldBe work.sourceIdentifier
                identifiedWork.state.canonicalId shouldBe CanonicalId(
                  sentIds.head
                )

                // No failures, so no messages should be returned
                results.size shouldBe 0
            }
        }
    }
  }

  it("mints an identifier for a invisible work") {
    val msgSender = new MemoryMessageSender()
    val work = sourceWork().invisible()
    val inputIndex = createIndex(List(work))
    val outputIndex = mutable.Map.empty[String, Work[Identified]]

    withIdentifiersTable {
      identifiersTableConfig: IdentifiersTableConfig =>
        withIdMinterSQSLambda(
          identifiersTableConfig = identifiersTableConfig,
          msgSender = Some(msgSender),
          mergedIndex = inputIndex,
          identifiedIndex = outputIndex
        ) {

          idMinterSqsLambda =>
            eventuallyTableExists(identifiersTableConfig)

            val messages = List(
              SQSTestLambdaMessage(message = work.id)
            )

            whenReady(idMinterSqsLambda.processMessages(messages)) {
              results =>
                val sentId = msgSender.messages.map(_.body).head

                val identifiedWork = outputIndex(sentId)
                identifiedWork.sourceIdentifier shouldBe work.sourceIdentifier
                identifiedWork.state.canonicalId shouldBe CanonicalId(sentId)

                // No failures, so no messages should be returned
                results.size shouldBe 0
            }
        }
    }
  }

  it("mints an identifier for a redirected work") {
    val msgSender = new MemoryMessageSender()
    val work = sourceWork()
      .redirected(
        redirectTarget =
          IdState.Identifiable(sourceIdentifier = createSourceIdentifier)
      )
    val inputIndex = createIndex(List(work))
    val outputIndex = mutable.Map.empty[String, Work[Identified]]

    withIdentifiersTable {
      identifiersTableConfig: IdentifiersTableConfig =>
        withIdMinterSQSLambda(
          identifiersTableConfig = identifiersTableConfig,
          msgSender = Some(msgSender),
          mergedIndex = inputIndex,
          identifiedIndex = outputIndex
        ) {

          idMinterSqsLambda =>
            eventuallyTableExists(identifiersTableConfig)

            val messages = List(
              SQSTestLambdaMessage(message = work.id)
            )

            whenReady(idMinterSqsLambda.processMessages(messages)) {
              result =>
                val sentId = msgSender.messages.map(_.body).head
                val identifiedWork = outputIndex(sentId)
                identifiedWork.sourceIdentifier shouldBe work.sourceIdentifier
                identifiedWork.state.canonicalId shouldBe CanonicalId(sentId)
                identifiedWork
                  .asInstanceOf[Work.Redirected[Identified]]
                  .redirectTarget
                  .canonicalId
                  .underlying shouldNot be(empty)

                // No failures, so no messages should be returned
                result.size shouldBe 0
            }
        }
    }
  }

  it("continues if something fails processing a message") {
    val msgSender = new MemoryMessageSender()
    val work = sourceWork()
    val inputIndex = createIndex(List(work))
    val outputIndex = mutable.Map.empty[String, Work[Identified]]

    withIdentifiersTable {
      identifiersTableConfig: IdentifiersTableConfig =>
        withIdMinterSQSLambda(
          identifiersTableConfig = identifiersTableConfig,
          msgSender = Some(msgSender),
          mergedIndex = inputIndex,
          identifiedIndex = outputIndex
        ) {

          idMinterSqsLambda =>
            eventuallyTableExists(identifiersTableConfig)
            val goodMessage = SQSTestLambdaMessage(message = work.id)

            val badMessage =
              SQSTestLambdaMessage(message = "this_work_id_does_not_exist")

            val messages = List(goodMessage, badMessage)

            whenReady(idMinterSqsLambda.processMessages(messages)) {
              result =>
                val sentIds = msgSender.messages.map(_.body)
                sentIds.size shouldBe 1

                val identifiedWork = outputIndex(sentIds.head)
                identifiedWork.sourceIdentifier shouldBe work.sourceIdentifier
                identifiedWork.state.canonicalId shouldBe CanonicalId(
                  sentIds.head
                )

                // One failure, so one message should be returned
                result.size shouldBe 1
                result.head shouldBe a[SQSLambdaMessageFailedRetryable]
                result.head.messageId shouldBe badMessage.messageId
            }
        }
    }
  }
}
