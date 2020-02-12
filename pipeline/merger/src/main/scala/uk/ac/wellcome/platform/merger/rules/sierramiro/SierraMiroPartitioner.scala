package uk.ac.wellcome.platform.merger.rules.sierramiro

import uk.ac.wellcome.models.work.internal.{
  BaseWork,
  IdentifierType,
  UnidentifiedWork,
}
import uk.ac.wellcome.platform.merger.rules.WorkTagPartitioner

trait SierraMiroPartitioner extends WorkTagPartitioner {

  protected def tagWork(work: BaseWork): WorkTag =
    work match {
      case work: UnidentifiedWork if isSierraWork(work) => Target
      case work: UnidentifiedWork if isMiroWork(work)   => Redirected
      case _                                            => PassThrough
    }

  private def isSierraWork(work: UnidentifiedWork): Boolean =
    work.sourceIdentifier.identifierType ==
      IdentifierType("sierra-system-number")

  private def isMiroWork(work: UnidentifiedWork): Boolean =
    work.sourceIdentifier.identifierType ==
      IdentifierType("miro-image-number")
}
