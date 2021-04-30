package uk.ac.wellcome.platform.inference_manager.models

import uk.ac.wellcome.platform.inference_manager.adapters.InferrerResponse

case class AspectRatioInferrerResponse(aspect_ratio: Option[Float])
    extends InferrerResponse
