package weco.lambda

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import io.circe.Decoder
import ujson.Value
import weco.json.JsonUtil.fromJson

import scala.collection.JavaConverters._
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

trait SQSLambdaMessageHandle {
  val messageId: String
}

trait SQSLambdaExtractedMessage extends SQSLambdaMessageHandle

case class SQSLambdaMessageFailedExtraction(messageId: String, messageBody: String, error: Throwable)
  extends SQSLambdaExtractedMessage

case class SQSLambdaMessage[T](messageId: String, message: T)
  extends SQSLambdaExtractedMessage

trait SQSLambdaMessageResult extends SQSLambdaMessageHandle

trait SQSLambdaMessageFailure extends SQSLambdaMessageResult {
  val error: Throwable
}

case class SQSLambdaMessageFailedRetryable(messageId: String, error: Throwable)
  extends SQSLambdaMessageFailure

case class SQSLambdaMessageFailedPermanent(messageId: String, error: Throwable)
  extends SQSLambdaMessageFailure

case class SQSLambdaMessageProcessed(messageId: String)
  extends SQSLambdaMessageResult

object SQSEventOps {

  /** Messages consumed by Lambda from SQS are taken from a queue populated by
    * an SNS topic. The actual message we are interested in is a String
    * containing the path. However, the matryoshka-like nature of these things
    * means the lambda receives
    *   - an event containing
    *   - a `Records` list, each Record containing
    *   - an SQS Message with a JSON body containing
    *   - an SNS notification containing
    *   - a `Message`, which is the actual content we want
    */
  implicit class ExtractTFromSqsEvent(event: SQSEvent) {
    def extractLambdaEvents[T]()(implicit decoder: Decoder[T], ct: ClassTag[T]): List[Either[SQSLambdaMessageFailedExtraction, SQSLambdaMessage[T]]] = {
      event.getRecords.asScala.toList.map { message =>
        (for {
          messageBodyJson <- Try(ujson.read(message.getBody)).recover {
            case e => throw new Error(s"Failed to parse message body: ${e.getMessage}")
          }
          messageValue <- (for {
            obj <- messageBodyJson.objOpt
            messageJson <- obj.get("Message")
          } yield messageJson).map(Success(_)).getOrElse(
            Failure(new Error("Failed to extract Message object, incorrect format?"))
          )
          decodedMessage <- ct.runtimeClass match {
            case c if c == classOf[String] => Success(messageValue.str.asInstanceOf[T])
            case _ => fromJson[T](messageValue.str).toEither.toTry.recover {
                case e => throw new Error(s"Failed to decode inner message: ${e.getMessage}")
            }
          }
        } yield SQSLambdaMessage(
          messageId = message.getMessageId,
          message = decodedMessage
        )) match {
            case Success(value) => Right(value)
            case Failure(e) => Left(SQSLambdaMessageFailedExtraction(
                messageId = message.getMessageId,
                messageBody = message.getBody,
                error = e
            ))
        }
      }
    }

    def extract[T]()(implicit decoder: Decoder[T], ct: ClassTag[T]): List[T] =
      event.getRecords.asScala.toList.flatMap(extractFromMessage[T](_))

    private def extractFromMessage[T](
      message: SQSMessage
    )(implicit decoder: Decoder[T], ct: ClassTag[T]): Option[T] =
      ujson.read(message.getBody).obj.get("Message").flatMap {
        value: Value =>
          {
            ct.runtimeClass match {
              case c if c == classOf[String] => Some(value.str.asInstanceOf[T])
              case _                         => fromJson[T](value.str).toOption
            }
          }
      }
  }
}
