package uk.ac.wellcome.platform.transformer.calm.data

import uk.ac.wellcome.models.work.internal.WorkType
import uk.ac.wellcome.models.work.internal.WorkType.{Linked, Unlinked}
import uk.ac.wellcome.platform.transformer.calm.exceptions.SierraTransformerException

object SierraMaterialTypes {

  def fromCode(code: String): WorkType = {
    code.toList match {
      case List(c) =>
        WorkType.fromCode(c.toString) match {
          case Some(workType: Unlinked) => workType
          case Some(workType: Linked)   => workType.linksTo
          case None =>
            throw SierraTransformerException(s"Unrecognised work type code: $c")
        }
      case _ =>
        throw SierraTransformerException(
          s"Work type code is not a single character: <<$code>>")
    }
  }
}
