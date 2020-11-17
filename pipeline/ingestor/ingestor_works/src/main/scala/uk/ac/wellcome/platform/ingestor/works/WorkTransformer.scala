package uk.ac.wellcome.platform.ingestor.works

import uk.ac.wellcome.models.work.internal.Work
import uk.ac.wellcome.models.work.internal.WorkState.{Derived, Identified}

object WorkTransformer {
  val deriveData: Work[Identified] => Work[Derived] =
    work => work.transition[Derived]()
}
