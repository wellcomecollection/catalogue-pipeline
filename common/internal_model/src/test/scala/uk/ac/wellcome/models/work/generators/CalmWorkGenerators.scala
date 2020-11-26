package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.models.work.internal.{Work, WorkState}

trait CalmWorkGenerators extends WorkGenerators with ItemsGenerators {

  def calmSourceWork(): Work.Visible[WorkState.Source] =
    sourceWork(sourceIdentifier = createCalmSourceIdentifier)
      .items(List(createCalmItem))

}
