package weco.pipeline.inference_manager.models

import weco.pipeline.inference_manager.services.MergedIdentifiedImage

import java.nio.file.Path

case class DownloadedImage(image: MergedIdentifiedImage, path: Path) {
  val pathString: String = path.toString
}
