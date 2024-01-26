package weco.pipeline.merger.models

import weco.catalogue.internal_model.identifiers.{DataState, IdState}
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.work.{Item, Work, WorkData}

trait WorkMergingOps {
  protected implicit class WorkMergingOps(
    work: Work.Visible[Identified]
  ) {
    def mapData(
      f: WorkData[Identified#WorkDataState] => WorkData[
        Identified#WorkDataState
      ]
    ): Work.Visible[Identified] =
      work.copy[Identified](data = f(work.data))

    def mapState(
      f: Identified => Identified
    ): Work.Visible[Identified] =
      work.copy(state = f(work.state))

    def withItemsInInternalWorks(
      items: List[Item[IdState.Minted]]
    ): Work.Visible[Identified] = {
      // Internal works are in TEI works. If they are merged with Sierra, we want the Sierra
      // items to be added to TEI internal works so that the user can request the item
      // containing that work without having to find the wrapping work.
      work
        .mapState {
          state =>
            state.copy(internalWorkStubs = state.internalWorkStubs.map {
              stub =>
                stub.copy(
                  workData = stub.workData.copy[DataState.Identified](items = items)
                )
            })
        }
    }
  }

}
