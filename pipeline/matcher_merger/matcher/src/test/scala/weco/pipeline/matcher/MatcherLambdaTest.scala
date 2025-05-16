package weco.pipeline.matcher

import com.typesafe.config.Config
import weco.lambda.Downstream
import weco.pipeline.matcher.config.{MatcherConfig, MatcherConfigurable}
import weco.pipeline.matcher.matcher.StoredWorksMatcher

class MatcherLambdaTest {

  class SUT extends MatcherSQSLambda[MatcherConfig] with MatcherConfigurable {

    override protected val worksMatcher: StoredWorksMatcher = ???
    override protected val downstream: Downstream = ???
  }
}
