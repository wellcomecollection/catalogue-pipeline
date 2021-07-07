package weco.catalogue.internal_model.work.generators

import weco.catalogue.internal_model.work.{Work, WorkState}

trait TeiWorkGenerators extends WorkGenerators with ItemsGenerators {
  def teiIdentifiedWork(): Work.Visible[WorkState.Identified] =
    identifiedWork(sourceIdentifier = createTeiSourceIdentifier)
}
