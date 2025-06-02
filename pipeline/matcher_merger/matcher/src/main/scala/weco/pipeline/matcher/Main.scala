package weco.pipeline.matcher
import grizzled.slf4j.Logging
import weco.lambda._
import weco.pipeline.matcher.config.{MatcherConfig, MatcherConfigurable}
import weco.pipeline.matcher.matcher.StoredWorksMatcher

import org.scanamo.generic.auto._
import scala.language.higherKinds

object Main
    extends MatcherSQSLambda[MatcherConfig]
    with MatcherConfigurable
    with Logging {

  override protected val worksMatcher: StoredWorksMatcher = StoredWorksMatcher(
    config.elasticConfig,
    config.index,
    config.dynamoConfig,
    config.dynamoLockDAOConfig
  )
  override protected val downstream: Downstream = Downstream(
    config.downstreamConfig
  )
}
