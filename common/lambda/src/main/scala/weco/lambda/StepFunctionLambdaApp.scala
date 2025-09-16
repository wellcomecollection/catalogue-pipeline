package weco.lambda

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import grizzled.slf4j.Logging
import io.circe.{Decoder, Encoder, Json, JsonObject}
import io.circe.syntax._
import org.apache.pekko.actor.ActorSystem

import scala.collection.JavaConverters._
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/** Base class for AWS Lambda functions that are invoked by Step Functions.
  *
  * AWS Java runtime will (when no specific POJO is provided) deserialise JSON
  * objects to LinkedHashMap[String, AnyRef]. We adapt that generic structure
  * into a typed InputType using Circe, and encode the typed OutputType back
  * into a LinkedHashMap for the runtime to serialise as JSON.
  */
abstract class StepFunctionLambdaApp[
  InputType,
  OutputType,
  Config <: ApplicationConfig
]()(
  implicit val inputDecoder: Decoder[InputType],
  implicit val outputEncoder: Encoder[OutputType],
  val ct: ClassTag[InputType]
) extends RequestHandler[java.util.LinkedHashMap[String, AnyRef], java.util.LinkedHashMap[String, AnyRef]]
    with LambdaConfigurable[Config]
    with Logging {

  // 15 minutes is the maximum time allowed for a lambda to run, as of 2024-12-19
  protected val maximumExecutionTime: FiniteDuration = 15.minutes

  implicit val actorSystem: ActorSystem =
    ActorSystem("main-actor-system")
  implicit val ec: ExecutionContext =
    actorSystem.dispatcher

  // --- JSON <-> Java object graph conversion helpers ---

  protected def anyRefToJson(value: AnyRef): Json = value match {
    case null => Json.Null
    case m: java.util.Map[_, _] @unchecked =>
      val fields = m.asInstanceOf[java.util.Map[String, AnyRef]].asScala.map {
        case (k, v) => (k, anyRefToJson(v))
      }
      Json.obj(fields.toSeq: _*)
    case l: java.util.List[_] @unchecked =>
      Json.fromValues(l.asInstanceOf[java.util.List[AnyRef]].asScala.map(anyRefToJson))
    case b: java.lang.Boolean => Json.fromBoolean(b.booleanValue())
    case n: java.lang.Integer => Json.fromInt(n.intValue())
    case n: java.lang.Long => Json.fromLong(n.longValue())
    case n: java.lang.Double => Json.fromDoubleOrNull(n.doubleValue())
    case n: java.lang.Float => Json.fromFloatOrNull(n.floatValue())
    case n: java.math.BigDecimal => Json.fromBigDecimal(scala.math.BigDecimal(n))
    case n: java.lang.Number => Json.fromBigDecimal(BigDecimal(n.toString))
    case s: String => Json.fromString(s)
    case other => Json.fromString(other.toString) // Fallback
  }

  protected def jsonToAnyRef(json: Json): AnyRef = json.fold[AnyRef](
    null,
    bool => java.lang.Boolean.valueOf(bool),
    num => num.toBigDecimal.map { bd =>
      if (bd.isValidInt) Int.box(bd.toInt)
      else if (bd.isValidLong) Long.box(bd.toLong)
      else new java.math.BigDecimal(bd.toString)
    }.orNull,
    str => str,
    arr => {
      val list = new java.util.ArrayList[AnyRef](arr.size)
      arr.foreach { j => list.add(jsonToAnyRef(j)) }
      list
    },
    obj => jsonObjectToMap(obj)
  )

  protected def jsonObjectToMap(obj: JsonObject): java.util.LinkedHashMap[String, AnyRef] = {
    val map = new java.util.LinkedHashMap[String, AnyRef]()
    obj.toMap.foreach { case (k, v) => map.put(k, jsonToAnyRef(v)) }
    map
  }

  protected def javaMapToJson(map: java.util.LinkedHashMap[String, AnyRef]): Json = anyRefToJson(map)

  /** Implementations supply business logic here. */
  def processRequest(input: InputType): Future[OutputType]

  /** AWS Lambda entry point handling LinkedHashMap-based (already parsed) JSON. */
  override def handleRequest(
    rawInput: java.util.LinkedHashMap[String, AnyRef],
    context: Context
  ): java.util.LinkedHashMap[String, AnyRef] = {
    info(
      s"Processing Step Function request (Java Map); expected input type: ${ct.runtimeClass.getSimpleName}; fieldCount=${rawInput.size()}"
    )
    debug(
      s"Lambda context - request ID: ${context.getAwsRequestId}, remaining time: ${context.getRemainingTimeInMillis}ms"
    )

    // Decode
    val inputJson = javaMapToJson(rawInput)
    val parsedInput: InputType = inputJson.as[InputType] match {
      case Left(err) =>
        error(s"Failed to decode input JSON: ${err.getMessage}")
        throw new RuntimeException(s"Failed to decode input: ${err.getMessage}", err)
      case Right(value) => value
    }

    // Process with timeout + recovery logging
    val output: OutputType = Try {
      Await.result(
        processRequest(parsedInput).recover { case ex: Exception =>
          error(s"Error processing Step Function request: ${ex.getMessage}", ex)
          throw ex
        },
        maximumExecutionTime
      )
    } match {
      case Success(o) =>
        info("Successfully processed Step Function request")
        o
      case Failure(ex) =>
        error(s"Failed to process Step Function request: ${ex.getMessage}", ex)
        throw ex
    }

    // Encode
    val outputJson = output.asJson
    outputJson.asObject match {
      case Some(obj) => jsonObjectToMap(obj)
      case None =>
        error("Output JSON was not an object; cannot serialise to LinkedHashMap")
        throw new RuntimeException("Output JSON must be a JSON object")
    }
  }
}

