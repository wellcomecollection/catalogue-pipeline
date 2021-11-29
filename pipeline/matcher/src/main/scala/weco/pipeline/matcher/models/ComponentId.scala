package weco.pipeline.matcher.models

import org.apache.commons.codec.digest.DigestUtils
import weco.catalogue.internal_model.identifiers.CanonicalId

object ComponentId {

  /** This is shared by all the Works in the same component -- i.e., all the
    * Works that are matched together.
    *
    * Note that this is based on the *unversioned* identifiers.  This means the
    * component identifier is stable across different versions of a Work.
    */
  def apply(ids: CanonicalId*): String =
    DigestUtils.sha256Hex(ids.sorted.map(_.underlying).mkString("+"))

  def apply(ids: List[CanonicalId]): String = apply(ids: _*)
}
