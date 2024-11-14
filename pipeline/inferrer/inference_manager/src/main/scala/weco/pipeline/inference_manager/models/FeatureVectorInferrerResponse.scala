package weco.pipeline.inference_manager.models

import weco.pipeline.inference_manager.adapters.InferrerResponse

// The type of the response from the inferrer, for Circe's decoding
case class FeatureVectorInferrerResponse(
  features_b64: String,
) extends InferrerResponse
