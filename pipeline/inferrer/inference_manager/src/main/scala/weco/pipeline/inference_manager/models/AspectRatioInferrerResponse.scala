package weco.pipeline.inference_manager.models

import weco.pipeline.inference_manager.adapters.InferrerResponse

case class AspectRatioInferrerResponse(aspect_ratio: Option[Float]) extends InferrerResponse
