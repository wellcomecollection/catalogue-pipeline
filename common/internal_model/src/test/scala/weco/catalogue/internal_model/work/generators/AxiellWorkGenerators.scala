package weco.catalogue.internal_model.work.generators

import weco.catalogue.internal_model.work.{Work, WorkState}

trait AxiellWorkGenerators extends WorkGenerators with ItemsGenerators {

  private def createAxiellItem = createCalmItem

  def axiellIdentifiedWork(): Work.Visible[WorkState.Identified] =
    identifiedWork(sourceIdentifier = createAxiellSourceIdentifier)
      .items(List(createAxiellItem))
}
