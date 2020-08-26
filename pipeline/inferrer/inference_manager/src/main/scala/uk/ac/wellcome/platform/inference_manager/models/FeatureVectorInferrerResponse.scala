package uk.ac.wellcome.platform.inference_manager.models

import uk.ac.wellcome.platform.inference_manager.adapters.InferrerResponse

// The type of the response from the inferrer, for Circe's decoding
case class FeatureVectorInferrerResponse(features_b64: String,
                                         lsh_encoded_features: List[String])
    extends InferrerResponse
