package weco.pipeline.ingestor.works

import weco.catalogue.internal_model.work.WorkState.{Denormalised, Indexed}
import weco.catalogue.internal_model.work.Work

object WorkTransformer {
  val deriveData: Work[Denormalised] => Work[Indexed] =
    work => work.transition[Indexed]()
}
