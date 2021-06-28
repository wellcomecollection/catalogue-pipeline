package weco.pipeline.transformer.sierra.data

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.work.Format
import weco.catalogue.internal_model.work.Format.{Linked, Unlinked}

object SierraMaterialTypes extends Logging {

  def fromCode(code: String): Option[Format] =
    code.toList match {
      case List(c) =>
        Format.fromCode(c.toString) match {
          case Some(format: Unlinked) => Some(format)
          case Some(format: Linked)   => Some(format.linksTo)
          case None =>
            warn(s"Unrecognised work type code: $c")
            None
        }
      case _ =>
        warn(s"Work type code is not a single character: <<$code>>")
        None
    }
}
