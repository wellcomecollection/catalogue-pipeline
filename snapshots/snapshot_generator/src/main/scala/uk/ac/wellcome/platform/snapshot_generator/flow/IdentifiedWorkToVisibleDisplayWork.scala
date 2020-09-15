package uk.ac.wellcome.platform.snapshot_generator.flow

import akka.NotUsed
import akka.stream.scaladsl.Flow
import uk.ac.wellcome.display.models.DisplayWork
import uk.ac.wellcome.models.work.internal._
import WorkState.Identified

object IdentifiedWorkToVisibleDisplayWork {
  def apply(
    toDisplayWork: Work.Standard[Identified] => DisplayWork): Flow[Work[Identified], DisplayWork, NotUsed] =
    Flow[Work[Identified]]
      .collect { case work: Work.Standard[Identified] => work }
      .map { toDisplayWork(_) }
}
