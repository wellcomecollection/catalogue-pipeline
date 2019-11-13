package uk.ac.wellcome.platform.merger.logging

import uk.ac.wellcome.models.work.internal.{TransformedBaseWork, UnidentifiedWork}

trait MergerLogging {
  def describeWorkPair(workA: UnidentifiedWork, workB: TransformedBaseWork) =
    s"(id=${workA.sourceIdentifier.value}) and (id=${workB.sourceIdentifier.value})"

  def describeWorkPairWithItems(workA: UnidentifiedWork,
                                workB: TransformedBaseWork): String =
    s"(id=${workA.sourceIdentifier.value}, itemsCount=${workA.data.items.size}) and " +
      s"(id=${workB.sourceIdentifier.value}, itemsCount=${workB.data.items.size})"
}
