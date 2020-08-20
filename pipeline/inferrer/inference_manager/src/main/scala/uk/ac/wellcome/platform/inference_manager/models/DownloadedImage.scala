package uk.ac.wellcome.platform.inference_manager.models

import java.nio.file.Path

import uk.ac.wellcome.platform.inference_manager.services.MergedIdentifiedImage

case class DownloadedImage(image: MergedIdentifiedImage, path: Path) {
  val pathString: String = path.toString
}
