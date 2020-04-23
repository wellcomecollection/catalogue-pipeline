package uk.ac.wellcome.platform.inference_manager.fixtures

import java.nio.{ByteBuffer, ByteOrder}
import java.util.Base64

object Encoding {
  def toLittleEndianBase64(floats: List[Float]): String = {
    val bytes = ByteBuffer
      .allocate(4 * floats.size)
      .order(ByteOrder.LITTLE_ENDIAN)
    floats.foreach { bytes.putFloat }
    Base64.getEncoder.encodeToString(bytes.array())
  }
}
