package weco.lambda.helpers

import weco.fixtures.RandomGenerators
import weco.lambda.SQSLambdaMessage

trait LambdaFixtures extends RandomGenerators {
  protected object SQSTestLambdaMessage {
    def apply[T](message: T): SQSLambdaMessage[T] =
      SQSLambdaMessage(messageId = randomUUID.toString, message = message)
  }
}
