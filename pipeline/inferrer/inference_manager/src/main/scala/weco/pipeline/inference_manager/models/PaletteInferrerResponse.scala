package weco.pipeline.inference_manager.models

import weco.pipeline.inference_manager.adapters.InferrerResponse

case class PaletteInferrerResponse(
  palette_embedding: String,
  average_color_hex: String,
) extends InferrerResponse
