/* IMPORTANT:
 *
 * This file contains code for generating swagger docs from Scala case classes, and is mirrored from:
 * https://github.com/swagger-akka-http/swagger-scala-module
 *
 * I could not get it working by depending on that library, as encountered the following runtime error:
 * java.lang.NoSuchMethodError: com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
 * This appears to be due to differences in Jackson version needed for our version of Scala and that
 * used in the library.
 */
package com.github.swagger.scala.converter

import scala.language.existentials
import java.lang.reflect.ParameterizedType
import java.util.Iterator

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.`type`.ReferenceType
import com.fasterxml.jackson.module.scala.{
  DefaultScalaModule,
  JsonScalaEnumeration
}
import io.swagger.v3.core.converter._
import io.swagger.v3.core.jackson.ModelResolver
import io.swagger.v3.core.util.{Json, PrimitiveType}
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.{Schema => SchemaAnnotation}
import io.swagger.v3.oas.models.media.Schema

class AnnotatedTypeForOption extends AnnotatedType

object SwaggerScalaModelConverter {
  Json.mapper().registerModule(new DefaultScalaModule())
}

class SwaggerScalaModelConverter extends ModelResolver(Json.mapper()) {
  SwaggerScalaModelConverter

  override def resolve(`type`: AnnotatedType,
                       context: ModelConverterContext,
                       chain: Iterator[ModelConverter]): Schema[_] = {
    val javaType = _mapper.constructType(`type`.getType)
    val cls = javaType.getRawClass

    matchScalaPrimitives(`type`, cls).getOrElse {
      // Unbox scala options
      val annotatedOverrides = getRequiredSettings(`type`)
      if (_isOptional(`type`, cls)) {
        val baseType =
          if (annotatedOverrides.headOption.getOrElse(false))
            new AnnotatedType()
          else new AnnotatedTypeForOption()
        resolve(nextType(baseType, `type`, cls, javaType), context, chain)
      } else if (!annotatedOverrides.headOption.getOrElse(true)) {
        resolve(
          nextType(new AnnotatedTypeForOption(), `type`, cls, javaType),
          context,
          chain)
      } else if (chain.hasNext) {
        val nextResolved = Option(chain.next().resolve(`type`, context, chain))
        nextResolved match {
          case Some(property) => {
            setRequired(`type`)
            property
          }
          case None => null
        }
      } else {
        null
      }
    }
  }

  private def getRequiredSettings(`type`: AnnotatedType): Seq[Boolean] =
    `type` match {
      case _: AnnotatedTypeForOption => Seq.empty
      case _ => {
        nullSafeList(`type`.getCtxAnnotations).collect {
          case p: Parameter        => p.required()
          case s: SchemaAnnotation => s.required()
        }
      }
    }

  private def matchScalaPrimitives(
    `type`: AnnotatedType,
    nullableClass: Class[_]): Option[Schema[_]] = {
    val annotations =
      Option(`type`.getCtxAnnotations).map(_.toSeq).getOrElse(Seq.empty)
    annotations.collectFirst { case ann: JsonScalaEnumeration => ann } match {
      case Some(enumAnnotation) => {
        val pt = enumAnnotation
          .value()
          .getGenericSuperclass
          .asInstanceOf[ParameterizedType]
        val args = pt.getActualTypeArguments
        val cls = args(0).asInstanceOf[Class[Enumeration]]
        getEnumerationInstance(cls).map { enum =>
          val sp: Schema[String] =
            PrimitiveType.STRING.createProperty().asInstanceOf[Schema[String]]
          setRequired(`type`)
          enum.values.iterator.foreach { v =>
            sp.addEnumItemObject(v.toString)
          }
          sp
        }
      }
      case _ => {
        Option(nullableClass).flatMap { cls =>
          if (cls == classOf[BigDecimal]) {
            val dp = PrimitiveType.DECIMAL.createProperty()
            setRequired(`type`)
            Some(dp)
          } else if (cls == classOf[BigInt]) {
            val ip = PrimitiveType.INT.createProperty()
            setRequired(`type`)
            Some(ip)
          } else {
            None
          }
        }
      }
    }
  }

  def _isOptional(annotatedType: AnnotatedType, cls: Class[_]): Boolean = {
    annotatedType.getType match {
      case _: ReferenceType if isOption(cls) => true
      case _                                 => false
    }
  }

  private def underlyingJavaType(annotatedType: AnnotatedType,
                                 cls: Class[_],
                                 javaType: JavaType): JavaType = {
    annotatedType.getType match {
      case rt: ReferenceType => rt.getContentType
      case _                 => javaType
    }
  }

  private def nextType(baseType: AnnotatedType,
                       `type`: AnnotatedType,
                       cls: Class[_],
                       javaType: JavaType): AnnotatedType = {
    baseType
      .`type`(underlyingJavaType(`type`, cls, javaType))
      .ctxAnnotations(`type`.getCtxAnnotations)
      .parent(`type`.getParent)
      .schemaProperty(`type`.isSchemaProperty)
      .name(`type`.getName)
      .propertyName(`type`.getPropertyName)
      .resolveAsRef(`type`.isResolveAsRef)
      .jsonViewAnnotation(`type`.getJsonViewAnnotation)
      .skipOverride(`type`.isSkipOverride)
  }

  override def _isOptionalType(propType: JavaType): Boolean = {
    isOption(propType.getRawClass) || super._isOptionalType(propType)
  }

  override def _isSetType(cls: Class[_]): Boolean = {
    val setInterfaces = cls.getInterfaces.find { interface =>
      interface == classOf[scala.collection.Set[_]]
    }
    setInterfaces.isDefined || super._isSetType(cls)
  }

  private def setRequired(annotatedType: AnnotatedType): Unit =
    annotatedType match {
      case _: AnnotatedTypeForOption => // not required
      case _ => {
        val required =
          getRequiredSettings(annotatedType).headOption.getOrElse(true)
        if (required) {
          Option(annotatedType.getParent).foreach { parent =>
            Option(annotatedType.getPropertyName).foreach { n =>
              addRequiredItem(parent, n)
            }
          }
        }
      }
    }

  private def getEnumerationInstance(cls: Class[_]): Option[Enumeration] = {
    if (cls.getFields.map(_.getName).contains("MODULE$")) {
      val javaUniverse = scala.reflect.runtime.universe
      val m =
        javaUniverse.runtimeMirror(Thread.currentThread().getContextClassLoader)
      val moduleMirror = m.reflectModule(m.staticModule(cls.getName))
      moduleMirror.instance match {
        case enumInstance: Enumeration => Some(enumInstance)
        case _                         => None
      }
    } else None
  }

  private def isOption(cls: Class[_]): Boolean = cls == classOf[scala.Option[_]]

  private def nullSafeList[T](array: Array[T]): List[T] = Option(array) match {
    case None      => List.empty[T]
    case Some(arr) => arr.toList
  }
}
