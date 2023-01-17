package weco.pipeline.inference_manager.models

import weco.pipeline.inference_manager.adapters.InferrerResponse

case class PaletteInferrerResponse(
  palette: List[String],
  average_color_hex: String,
  hash_params: HashParams
) extends InferrerResponse

case class HashParams(bin_sizes: List[List[Int]], bin_minima: List[Float])
