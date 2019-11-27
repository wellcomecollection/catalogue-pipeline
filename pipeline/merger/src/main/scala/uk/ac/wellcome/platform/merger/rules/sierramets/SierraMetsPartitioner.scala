package uk.ac.wellcome.platform.merger.rules.sierramets

import uk.ac.wellcome.models.work.internal.{
  BaseWork,
  IdentifierType,
  UnidentifiedInvisibleWork,
  UnidentifiedWork
}
import uk.ac.wellcome.platform.merger.rules.WorkTagPartitioner

trait SierraMetsPartitioner extends WorkTagPartitioner {

  def tagWork(work: BaseWork): WorkTag =
    work match {
      case work: UnidentifiedWork if isSierraWork(work)        => Target
      case work: UnidentifiedInvisibleWork if isMetsWork(work) => Redirected
      case _                                                   => PassThrough
    }

  private def isSierraWork(work: UnidentifiedWork): Boolean =
    work.sourceIdentifier.identifierType == IdentifierType(
      "sierra-system-number")

  private def isMetsWork(work: UnidentifiedInvisibleWork): Boolean =
    work.sourceIdentifier.identifierType == IdentifierType("mets")
}
