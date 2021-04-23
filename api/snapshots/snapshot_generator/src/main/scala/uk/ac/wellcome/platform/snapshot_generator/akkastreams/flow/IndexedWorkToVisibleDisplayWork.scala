package uk.ac.wellcome.platform.snapshot_generator.akkastreams.flow

import akka.NotUsed
import akka.stream.scaladsl.Flow
import uk.ac.wellcome.display.models.DisplayWork
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Indexed

object IndexedWorkToVisibleDisplayWork {
  def apply(toDisplayWork: Work.Visible[Indexed] => DisplayWork)
    : Flow[Work[Indexed], DisplayWork, NotUsed] =
    Flow[Work[Indexed]]
      .collect { case work: Work.Visible[Indexed] => work }
      .map { toDisplayWork(_) }
}
