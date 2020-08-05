package uk.ac.wellcome.platform.inference_manager.models

import java.nio.file.Path

import uk.ac.wellcome.models.work.internal.{Identified, MergedImage, Minted}

case class DownloadedImage(image: MergedImage[Identified, Minted], path: Path) {
  val pathString: String = path.toString
  // Delete path and directories above it until deletion fails (the directory is not empty)
  def delete(pathToDelete: Path = path): Unit =
    if (pathToDelete.toFile.delete) {
      delete(pathToDelete.getParent)
    }
}
