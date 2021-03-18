package uk.ac.wellcome.relation_embedder.fixtures

import uk.ac.wellcome.models.work.generators.{ItemsGenerators, WorkGenerators}
import weco.catalogue.internal_model.work.WorkState.Merged
import uk.ac.wellcome.relation_embedder.{
  RelationWork,
  RelationWorkData,
  RelationWorkState
}
import weco.catalogue.internal_model.locations.AccessStatus
import weco.catalogue.internal_model.work.{CollectionPath, Work}

trait RelationGenerators extends WorkGenerators with ItemsGenerators {
  def work(path: String,
           isAvailableOnline: Boolean = false): Work.Visible[Merged] =
    mergedWork(createSourceIdentifierWith(value = path))
      .collectionPath(CollectionPath(path = path))
      .title(path)
      .items(if (isAvailableOnline) {
        List(createDigitalItemWith(accessStatus = AccessStatus.Open))
      } else Nil)

  def toRelationWork(work: Work[Merged]): RelationWork =
    RelationWork(
      data = RelationWorkData(
        title = work.data.title,
        collectionPath = work.data.collectionPath,
        workType = work.data.workType,
      ),
      state = RelationWorkState(
        canonicalId = work.state.canonicalId,
        availabilities = work.state.availabilities
      )
    )
}
