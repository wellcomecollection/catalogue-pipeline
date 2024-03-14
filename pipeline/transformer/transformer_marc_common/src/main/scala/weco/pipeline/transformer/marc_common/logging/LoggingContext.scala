package weco.pipeline.transformer.marc_common.logging

import grizzled.slf4j.Logging

class LoggingContext(context: String) extends Logging {
  def apply(message: String): String = s"[$context] $message"
}
