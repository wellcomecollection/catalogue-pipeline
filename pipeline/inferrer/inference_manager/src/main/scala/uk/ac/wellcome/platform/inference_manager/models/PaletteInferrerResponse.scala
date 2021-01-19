package uk.ac.wellcome.platform.inference_manager.models

import uk.ac.wellcome.platform.inference_manager.adapters.InferrerResponse

case class PaletteInferrerResponse(palette: List[String],
                                   hash_params: HashParams)
    extends InferrerResponse

case class HashParams(bin_sizes: List[List[Int]], bin_minima: List[Float])
