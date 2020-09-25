package com.github.swagger.scala.converter

import java.util.Iterator
import java.lang.reflect.Type
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.`type`.{
  CollectionLikeType,
  ReferenceType,
  SimpleType
}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import io.swagger.v3.core.converter._
import io.swagger.v3.oas.models.media.{ArraySchema, Schema}
import io.swagger.v3.core.jackson.AbstractModelConverter
import io.swagger.v3.core.util.Json

import uk.ac.wellcome.platform.api.models._
import uk.ac.wellcome.display.models._

/** Custom swagger model converter, used for resolving Scala types into OpenAPI schemas.
  *  https://github.com/swagger-api/swagger-core/wiki/Swagger-2.X---Extensions#extending-core-resolver
  *
  *  This correctly handles Option[_] and List[_] types.
  *  The previous version which was copy / pasted from swagger-scala-module created lots of
  *  unnecessary List* types, whereas here new types are not created and instead we just wrap
  *  within an ArraySchema.
  */
class SwaggerScalaModelConverter extends AbstractModelConverter(Json.mapper()) {

  Json.mapper().registerModule(new DefaultScalaModule())

  override def resolve(annotatedType: AnnotatedType,
                       context: ModelConverterContext,
                       chain: Iterator[ModelConverter]): Schema[_] = {
    val cls = getClass(annotatedType.getType)

    if (cls == classOf[Option[_]])
      resolve(containedType(annotatedType), context, chain)
    else if (cls == classOf[List[_]])
      new ArraySchema().items(
        resolve(containedType(annotatedType), context, chain)
      )
    else if (chain.hasNext) {
      val schema = chain.next().resolve(annotatedType, context, chain)
      if (cls == classOf[DisplayAggregation[_]])
        schema.name(s"${getAggregationClassName(annotatedType)}Aggregation")
      else if (cls == classOf[DisplayAggregationBucket[_]])
        schema.name(
          s"${getAggregationClassName(annotatedType)}AggregationBucket")
      else
        schema
    } else
      null
  }

  private def containedType(annotatedType: AnnotatedType): AnnotatedType =
    (new AnnotatedType)
      .`type`(getContentType(annotatedType))
      .ctxAnnotations(annotatedType.getCtxAnnotations)
      .parent(annotatedType.getParent)
      .schemaProperty(annotatedType.isSchemaProperty)
      .name(annotatedType.getName)
      .propertyName(annotatedType.getPropertyName)
      .resolveAsRef(annotatedType.isResolveAsRef)
      .jsonViewAnnotation(annotatedType.getJsonViewAnnotation)
      .skipOverride(annotatedType.isSkipOverride)

  private def getContentType(annotatedType: AnnotatedType): JavaType =
    annotatedType.getType match {
      case rt: ReferenceType      => rt.getContentType
      case ct: CollectionLikeType => ct.getContentType
      case st: SimpleType         => st.containedType(0)
      case t                      => throw new IllegalArgumentException(s"Unknown type $t")
    }

  private def getAggregationClassName(annotatedType: AnnotatedType): String = {
    val cls = getClass(getContentType(annotatedType))
    if (cls == classOf[DisplayGenre]) "Genre"
    else if (cls == classOf[DisplayFormat]) "Format"
    else if (cls == classOf[DisplayPeriod]) "Period"
    else if (cls == classOf[DisplaySubject]) "Subject"
    else if (cls == classOf[DisplayLanguage]) "Language"
    else if (cls == classOf[DisplayLicense]) "License"
    else if (cls == classOf[DisplayLocationTypeAggregation])
      "LocationTypeAggregation"
    else throw new IllegalArgumentException(s"Unknown class $cls")
  }

  private def getClass(t: Type): Class[_] = _mapper.constructType(t).getRawClass
}
