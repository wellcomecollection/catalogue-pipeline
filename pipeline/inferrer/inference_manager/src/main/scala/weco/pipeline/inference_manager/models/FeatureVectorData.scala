package weco.pipeline.inference_manager.models

case class FeatureVectorData(
  features1: List[Float],
  features2: List[Float],
  reducedFeatures: List[Float]
)
