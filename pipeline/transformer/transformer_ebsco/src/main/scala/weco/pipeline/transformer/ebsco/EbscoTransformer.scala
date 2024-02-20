package weco.pipeline.transformer.ebsco
import weco.catalogue.internal_model.identifiers.DataState
import weco.catalogue.internal_model.work.WorkData

class EbscoTransformer {

  def workDataFromMarcRecord(): WorkData[DataState.Unidentified] = {
    WorkData[DataState.Unidentified]()
  }
}
