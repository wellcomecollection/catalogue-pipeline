package weco.pipeline.matcher.exceptions

case class MatcherException(e: Throwable) extends Exception(e.getMessage)
