package weco.pipeline.matcher.models

import org.apache.commons.codec.digest.DigestUtils
import weco.catalogue.internal_model.identifiers.CanonicalId

object SubgraphId {

  /** This is shared by all the Works in the same subgraph -- all the Works that
    * should be processed together.
    *
    * Note that this is based on the *unversioned* identifiers. This means the
    * subgraph identifier is stable across different versions of a Work.
    */
  def apply(ids: CanonicalId*): String = {
    require(
      ids.toSet.size == ids.size,
      s"Passed duplicate IDs in SubgraphId: $ids"
    )

    DigestUtils.sha256Hex(ids.sorted.map(_.underlying).mkString("+"))
  }

  def apply(ids: Set[CanonicalId]): String = apply(ids.toList: _*)
}
