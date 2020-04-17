package uk.ac.wellcome.platform.inference_manager.fixtures

import java.nio.ByteBuffer
import java.util.Base64

object Encoding {
  def toBase64(floats: List[Float]): String = {
    val bytes = ByteBuffer.allocate(4 * floats.size)
    floats.foreach { bytes.putFloat }
    Base64.getEncoder.encodeToString(bytes.array())
  }
}
