package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.models.work.internal.{Work, WorkState}

trait CalmWorkGenerators extends WorkGenerators with ItemsGenerators {

  def calmIdentifiedWork(): Work.Visible[WorkState.Identified] =
    identifiedWork(sourceIdentifier = createCalmSourceIdentifier)
      .items(List(createCalmItem))

}
