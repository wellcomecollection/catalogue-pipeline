package weco.pipeline.relation_embedder.fixtures

import weco.catalogue.internal_model.locations.AccessStatus
import weco.catalogue.internal_model.work.WorkState.Merged
import weco.catalogue.internal_model.work.generators.{
  ItemsGenerators,
  WorkGenerators
}
import weco.catalogue.internal_model.work.{
  CollectionPath,
  Relation,
  Work,
  WorkType
}
import weco.pipeline.relation_embedder.models.{
  RelationWork,
  RelationWorkData,
  RelationWorkState
}

trait RelationGenerators extends WorkGenerators with ItemsGenerators {
  def work(
    path: String,
    isAvailableOnline: Boolean = false
  ): Work.Visible[Merged] =
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
        workType = work.data.workType
      ),
      state = RelationWorkState(
        canonicalId = work.state.canonicalId,
        availabilities = work.state.availabilities
      )
    )

  def relations(n: Int): List[Relation] = {
    List.fill(n)(
      new Relation(
        id = Some(createCanonicalId),
        title = None,
        collectionPath = None,
        workType = WorkType.Standard,
        depth = 0,
        numChildren = 1,
        numDescendents = 1
      )
    )
  }
}
