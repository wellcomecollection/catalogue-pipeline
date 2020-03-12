package uk.ac.wellcome.platform.inference_manager.models

// The type of the response from the inferrer, for Circe's decoding
case class InferrerResponse(features: List[Float],
                            lsh_encoded_features: List[String])
