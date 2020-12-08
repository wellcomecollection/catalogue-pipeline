package uk.ac.wellcome.platform.ingestor.works

import uk.ac.wellcome.models.work.internal.Work
import uk.ac.wellcome.models.work.internal.WorkState.{Denormalised, Indexed}

object WorkTransformer {
  val deriveData: Work[Denormalised] => Work[Indexed] =
    work => work.transition[Indexed]()
}
