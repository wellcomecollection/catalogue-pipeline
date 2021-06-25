package weco.catalogue.internal_model.work.generators

import weco.catalogue.internal_model.work.{Work, WorkState}

trait CalmWorkGenerators extends WorkGenerators with ItemsGenerators {

  def calmIdentifiedWork(): Work.Visible[WorkState.Identified] =
    identifiedWork(sourceIdentifier = createCalmSourceIdentifier)
      .items(List(createCalmItem))

}
