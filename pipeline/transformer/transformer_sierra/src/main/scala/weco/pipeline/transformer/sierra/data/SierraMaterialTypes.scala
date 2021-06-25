package weco.pipeline.transformer.sierra.data

import weco.catalogue.internal_model.work.Format
import weco.catalogue.internal_model.work.Format.{Linked, Unlinked}
import weco.pipeline.transformer.sierra.exceptions.SierraTransformerException

object SierraMaterialTypes {

  def fromCode(code: String): Format = {
    code.toList match {
      case List(c) =>
        Format.fromCode(c.toString) match {
          case Some(format: Unlinked) => format
          case Some(format: Linked)   => format.linksTo
          case None =>
            throw SierraTransformerException(s"Unrecognised work type code: $c")
        }
      case _ =>
        throw SierraTransformerException(
          s"Work type code is not a single character: <<$code>>")
    }
  }
}
