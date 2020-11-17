package uk.ac.wellcome.platform.snapshot_generator.akkastreams.flow

import akka.NotUsed
import akka.stream.scaladsl.Flow
import uk.ac.wellcome.display.models.DisplayWork
import uk.ac.wellcome.models.work.internal._
import WorkState.Derived

object DerivedWorkToVisibleDisplayWork {
  def apply(toDisplayWork: Work.Visible[Derived] => DisplayWork)
    : Flow[Work[Derived], DisplayWork, NotUsed] =
    Flow[Work[Derived]]
      .collect { case work: Work.Visible[Derived] => work }
      .map { toDisplayWork(_) }
}
