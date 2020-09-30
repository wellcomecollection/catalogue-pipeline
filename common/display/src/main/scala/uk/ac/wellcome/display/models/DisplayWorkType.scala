package uk.ac.wellcome.display.models

import uk.ac.wellcome.models.work.internal._

object DisplayWorkType {

  def apply(workType: WorkType): String =
    workType match {
      case WorkType.Standard   => "Work"
      case WorkType.Collection => "Collection"
      case WorkType.Series     => "Series"
      case WorkType.Section    => "Section"
    }
}
