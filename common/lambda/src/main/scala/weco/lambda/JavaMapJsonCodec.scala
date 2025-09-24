package weco.lambda

import io.circe.{Json, JsonObject}
import scala.collection.JavaConverters._
import grizzled.slf4j.Logging

/** Utility for converting between the untyped Java object graph produced by the
  * AWS Java Lambda runtime (LinkedHashMap / java.util.List / boxed primitives)
  * and Circe Json. This logic is extracted from the StepFunctionLambdaApp so it
  * can be reused and unit-tested independently.
  */

object JavaMapJsonCodec extends Logging {
  def anyRefToJson(value: AnyRef): Json = value match {
    case null => Json.Null
    case m: java.util.Map[_, _] @unchecked =>
      val fields = m.asInstanceOf[java.util.Map[String, AnyRef]].asScala.map {
        case (k, v) => (k, anyRefToJson(v))
      }
      Json.obj(fields.toSeq: _*)
    case l: java.util.List[_] @unchecked =>
      Json.fromValues(
        l.asInstanceOf[java.util.List[AnyRef]].asScala.map(anyRefToJson)
      )
    case b: java.lang.Boolean => Json.fromBoolean(b.booleanValue())
    case n: java.lang.Integer => Json.fromInt(n.intValue())
    case n: java.lang.Long    => Json.fromLong(n.longValue())
    case n: java.lang.Double  => Json.fromDoubleOrNull(n.doubleValue())
    case n: java.lang.Float   => Json.fromFloatOrNull(n.floatValue())
    case n: java.math.BigDecimal =>
      Json.fromBigDecimal(scala.math.BigDecimal(n))
    case n: java.lang.Number => Json.fromBigDecimal(BigDecimal(n.toString))
    case s: String           => Json.fromString(s)
    case other => {
      warn(s"Unexpected type in anyRefToJson: ${other.getClass}")
      Json.fromString(other.toString) // Fallback to toString
    }
  }

  def jsonToAnyRef(json: Json): AnyRef = json.fold[AnyRef](
    null,
    bool => java.lang.Boolean.valueOf(bool),
    num =>
      num.toBigDecimal.map {
        bd =>
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

  def jsonObjectToMap(
    obj: JsonObject
  ): java.util.LinkedHashMap[String, AnyRef] = {
    val map = new java.util.LinkedHashMap[String, AnyRef]()
    obj.toMap.foreach { case (k, v) => map.put(k, jsonToAnyRef(v)) }
    map
  }

  def javaMapToJson(map: java.util.LinkedHashMap[String, AnyRef]): Json =
    anyRefToJson(map)

  /** Convenience for tests: convert a top-level JSON object to a Java map. */
  def jsonToJavaMap(json: Json): java.util.LinkedHashMap[String, AnyRef] =
    json.asObject match {
      case Some(obj) => jsonObjectToMap(obj)
      case None =>
        throw new IllegalArgumentException("Top-level JSON must be an object")
    }
}
