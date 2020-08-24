package uk.ac.wellcome.platform.inference_manager.models

import uk.ac.wellcome.platform.inference_manager.adapters.InferrerResponse

case class PaletteInferrerResponse(palette: List[String])
    extends InferrerResponse
