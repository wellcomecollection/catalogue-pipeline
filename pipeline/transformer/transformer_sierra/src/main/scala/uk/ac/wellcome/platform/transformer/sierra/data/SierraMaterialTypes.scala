package uk.ac.wellcome.platform.transformer.sierra.data

import uk.ac.wellcome.models.work.internal.WorkType
import uk.ac.wellcome.models.work.internal.WorkType.{LinkedWorkType, UnlinkedWorkType}
import uk.ac.wellcome.platform.transformer.sierra.exceptions.SierraTransformerException

object SierraMaterialTypes {

  def fromCode(code: String): WorkType = {
    code.toList match {
      case List(c) =>
        WorkType.fromCode(c.toString) match {
          case Some(workType: UnlinkedWorkType) => workType
          case Some(workType: LinkedWorkType) => workType.linksTo
          case None =>
            throw SierraTransformerException(s"Unrecognised work type code: $c")
        }
      case _ =>
        throw SierraTransformerException(
          s"Work type code is not a single character: <<$code>>")
    }
  }
}
