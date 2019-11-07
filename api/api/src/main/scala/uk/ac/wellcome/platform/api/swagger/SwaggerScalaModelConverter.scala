package com.github.swagger.scala.converter

import java.util.Iterator

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.`type`.{CollectionLikeType, ReferenceType}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import io.swagger.v3.core.converter._
import io.swagger.v3.oas.models.media.{ArraySchema, Schema}
import io.swagger.v3.core.jackson.AbstractModelConverter
import io.swagger.v3.core.util.Json

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
    val javaType = _mapper.constructType(annotatedType.getType)
    val cls = javaType.getRawClass

    if (cls == classOf[Option[_]])
      resolve(containedType(annotatedType, cls, javaType), context, chain)
    else if (cls == classOf[List[_]])
      new ArraySchema().items(
        resolve(containedType(annotatedType, cls, javaType), context, chain)
      )
    else if (chain.hasNext)
      chain.next().resolve(annotatedType, context, chain)
    else
      null
  }

  private def containedType(annotatedType: AnnotatedType,
                            cls: Class[_],
                            javaType: JavaType): AnnotatedType =
    (new AnnotatedType)
      .`type`(getContentType(annotatedType).getOrElse(javaType))
      .ctxAnnotations(annotatedType.getCtxAnnotations)
      .parent(annotatedType.getParent)
      .schemaProperty(annotatedType.isSchemaProperty)
      .name(annotatedType.getName)
      .propertyName(annotatedType.getPropertyName)
      .resolveAsRef(annotatedType.isResolveAsRef)
      .jsonViewAnnotation(annotatedType.getJsonViewAnnotation)
      .skipOverride(annotatedType.isSkipOverride)

  private def getContentType(annotatedType: AnnotatedType): Option[JavaType] =
    annotatedType.getType match {
      case rt: ReferenceType      => Some(rt.getContentType)
      case ct: CollectionLikeType => Some(ct.getContentType)
      case _                      => None
    }
}
