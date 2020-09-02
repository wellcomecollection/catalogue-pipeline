package uk.ac.wellcome.elasticsearch.elastic4s.searchtemplate

import com.sksamuel.elastic4s.{ElasticRequest, Handler, HttpEntity}

trait PutSearchTemplateHandlers {
  implicit object PutSearchTemplateHandlerrr
      extends Handler[PutSearchTemplateRequest, PutSearchTemplateResponse] {
    override def build(req: PutSearchTemplateRequest): ElasticRequest = {
      val endpoint = s"/_scripts/${req.id}"
      val body = PutSearchTemplateBuilderFn(req).string()
      val entity = HttpEntity(body, "application/json")
      ElasticRequest("POST", endpoint, entity)
    }
  }
}

case class PutSearchTemplateResponse(acknowledged: Boolean)
