package weco.pipeline.id_minter

import io.circe.Decoder
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{CanonicalId, IdState}
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.lambda._
import weco.lambda.behaviours.LambdaBehavioursStringInStringOut
import weco.lambda.helpers.LambdaFixtures
import weco.lambda.helpers.MemoryDownstream
import weco.pipeline.id_minter.config.models.IdentifiersTableConfig
import weco.pipeline.id_minter.utils.{DummyConfig, IdMinterSqsLambdaTestHelpers}

import scala.collection.mutable

class IdMinterSqsLambdaFeatureTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with IdMinterSqsLambdaTestHelpers
    with IntegrationPatience
    with Eventually
    with WorkGenerators
    with LambdaFixtures
    with LambdaBehavioursStringInStringOut[DummyConfig]
    with MemoryDownstream {

  describe("When input source identifiers match") {
    info(
      """multiple input messages with the same identifier
    only result in one record written and one message sent"""
    )
    val work = sourceWork()
    val inputIndex = createIndex(List(work))
    val outputIndex = mutable.Map.empty[String, Work[Identified]]
    val messageCount = 5
    val messages = (1 to messageCount).map {
      _ => SQSTestLambdaMessage(message = work.id)
    }
    withIdMinterSQSLambdaBuilder(
      mergedIndex = inputIndex,
      identifiedIndex = outputIndex
    ) {
      idMinterSqsLambdaBuilder =>
        it should behave like aTotalSuccess(
          idMinterSqsLambdaBuilder,
          messages,
          () => Seq(outputIndex.keys.loneElement),
          downstreamDescription =
            "sends one message downstream for each *unique* upstream message"
        )
    }
    it("sets the canonicalid of the document to the newly minted identifier") {
      val mintedId = outputIndex.keys.loneElement
      val identifiedWork = outputIndex(mintedId)
      identifiedWork.sourceIdentifier shouldBe work.sourceIdentifier
      identifiedWork.state.canonicalId shouldBe CanonicalId(
        mintedId
      )
    }
  }
  it("mints the same IDs where source identifiers match") {
    val work = sourceWork()
    val inputIndex = createIndex(List(work))
    val outputIndex = mutable.Map.empty[String, Work[Identified]]
    val downstream = new MemorySNSDownstream

    withIdMinterSQSLambdaBuilder(
      mergedIndex = inputIndex,
      identifiedIndex = outputIndex
    ) {
      idMinterSqsLambdaBuilder =>
        val idMinterSqsLambda = idMinterSqsLambdaBuilder(downstream)
        val messageCount = 5
        val messages = (1 to messageCount).map {
          _ => SQSTestLambdaMessage(message = work.id)
        }

        whenReady(idMinterSqsLambda.processMessages(messages)) {
          results =>
            val sentIds = downstream.msgSender.messages.map(_.body)

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

  it("mints an identifier for a invisible work") {
    val downstream = new MemorySNSDownstream
    val work = sourceWork().invisible()
    val inputIndex = createIndex(List(work))
    val outputIndex = mutable.Map.empty[String, Work[Identified]]

    withIdentifiersTable {
      identifiersTableConfig: IdentifiersTableConfig =>
        withIdMinterSQSLambda(
          identifiersTableConfig = identifiersTableConfig,
          memoryDownstream = downstream,
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
                val sentId = downstream.msgSender.messages.map(_.body).head

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
    val downstream = new MemorySNSDownstream
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
          memoryDownstream = downstream,
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
                val sentId = downstream.msgSender.messages.map(_.body).head
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
    val downstream = new MemorySNSDownstream
    val work = sourceWork()
    val inputIndex = createIndex(List(work))
    val outputIndex = mutable.Map.empty[String, Work[Identified]]

    withIdentifiersTable {
      identifiersTableConfig: IdentifiersTableConfig =>
        withIdMinterSQSLambda(
          identifiersTableConfig = identifiersTableConfig,
          memoryDownstream = downstream,
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
                val sentIds = downstream.msgSender.messages.map(_.body)
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

  override protected implicit val outputDecoder: Decoder[String] =
    Decoder.decodeString

  override protected def convertForComparison(
    results: Seq[String]
  ): Seq[String] = results
}
