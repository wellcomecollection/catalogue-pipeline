package uk.ac.wellcome.platform.inference_manager.models

case class FeatureVectorData(features1: List[Float],
                             features2: List[Float],
                             lshEncodedFeatures: List[String])
