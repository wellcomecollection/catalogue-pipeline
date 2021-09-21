package weco.pipeline.relation_embedder.fixtures

import weco.catalogue.internal_model.locations.AccessStatus
import weco.catalogue.internal_model.work.WorkState.Merged
import weco.catalogue.internal_model.work.generators.{
  ItemsGenerators,
  WorkGenerators
}
import weco.catalogue.internal_model.work.{RelationPath, Work}
import weco.pipeline.relation_embedder.{
  RelationWork,
  RelationWorkData,
  RelationWorkState
}

trait RelationGenerators extends WorkGenerators with ItemsGenerators {
  def work(path: String,
           isAvailableOnline: Boolean = false): Work.Visible[Merged] =
    mergedWork(createSourceIdentifierWith(value = path))
      .relationPath(RelationPath(path = path))
      .title(path)
      .items(if (isAvailableOnline) {
        List(createDigitalItemWith(accessStatus = AccessStatus.Open))
      } else Nil)

  def toRelationWork(work: Work[Merged]): RelationWork =
    RelationWork(
      data = RelationWorkData(
        title = work.data.title,
        relationPath = work.data.relationPath,
        workType = work.data.workType,
      ),
      state = RelationWorkState(
        canonicalId = work.state.canonicalId,
        availabilities = work.state.availabilities
      )
    )
}
