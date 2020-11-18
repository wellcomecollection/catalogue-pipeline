package uk.ac.wellcome.platform.ingestor.works

import uk.ac.wellcome.models.work.internal.Work
import uk.ac.wellcome.models.work.internal.WorkState.{Identified, Indexed}

object WorkTransformer {
  val deriveData: Work[Identified] => Work[Indexed] =
    work => work.transition[Indexed]()
}
