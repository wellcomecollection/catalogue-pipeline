package weco.pipeline.id_minter

import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.CanonicalId
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.lambda._
import weco.messaging.memory.MemoryMessageSender
import weco.pipeline.id_minter.config.models.IdentifiersTableConfig
import weco.pipeline.id_minter.utils.IdMinterSqsLambdaTestHelpers

import scala.collection.mutable

class IdMinterSqsLambdaTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with IdMinterSqsLambdaTestHelpers
    with IntegrationPatience
    with Eventually
    with WorkGenerators {

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
          mergedIndex = inputIndex, identifiedIndex = outputIndex) {
          idMinterSqsLambda =>
            eventuallyTableExists(identifiersTableConfig)

            val messageCount = 5
            val messages = (1 to messageCount).map {
              _ =>
                SQSLambdaMessage(
                  messageId = randomUUID.toString,
                  message = work.id
                )
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
}
