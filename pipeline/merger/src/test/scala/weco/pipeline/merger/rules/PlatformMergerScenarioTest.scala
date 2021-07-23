package weco.pipeline.merger.rules

import weco.pipeline.merger.MergerScenarioTest
import weco.pipeline.merger.services.{Merger, PlatformMerger}

class PlatformMergerScenarioTest extends MergerScenarioTest{
  override val merger: Merger = PlatformMerger
}
