package weco.catalogue.internal_model.index

import com.sksamuel.elastic4s.analysis.TokenFilter
import com.sksamuel.elastic4s.json.{XContentBuilder, XContentFactory}

// TODO: Patch back to elastic4s
case class AsciiFoldingTokenFilter(
  name: String,
  preserveOriginal: Option[Boolean] = None
) extends TokenFilter {

  override def build: XContentBuilder = {
    val b = XContentFactory.jsonBuilder()
    b.field("type", "asciifolding")
    preserveOriginal.foreach(b.field("preserve_original", _))
    b
  }
}
