package weco.pipeline.merger.models

import cats.data.State
import weco.pipeline.merger.services.PlatformMerger.MergeState

// TODO: The fact that retainSources is never used hints to me that this is all a bit of a relic.
// What this is used for is collecting all the sources that contribute to all the results in a
// Merge so that they can be redirected in the end.  The cats business here allows us to collect
// non-redirecting sources and redirecting sources as we go along, ensuring that for any given source
// the first redirection status (true or false) it gets wins.
// However, all contributing sources get redirect = true, so it could just be a set constructed from
// all the sources.
trait FieldMergeResultOps {
  protected implicit class MergeResultAccumulation[T](
    val result: FieldMergeResult[T]
  ) {
    def redirectSources: State[MergeState, T] = shouldRedirect(true)

    def retainSources: State[MergeState, T] = shouldRedirect(false)

    // If the state already contains a source, then don't change the existing `redirect` value
    // Otherwise, add the source with the current value.
    private def shouldRedirect(redirect: Boolean): State[MergeState, T] =
      State {
        prevState =>
          val nextState = result.sources.foldLeft(prevState) {
            case (state, source) if state.contains(source) => state
            case (state, source) => state + (source -> redirect)
          }
          (nextState, result.data)
      }
  }
}
