package uk.ac.wellcome.platform.transformer.mets.service

import scalaj.http.Http
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.platform.transformer.mets.model.Bag

class BagsRetriever(url: String) {
  def getBag(space: String, bagId: String): Option[Bag] = {

    val response = Http(s"$url/$space/$bagId").asString

    response.code match {
      case 200 => Some(fromJson[Bag](response.body).get)
      case _ => None
    }


  }

}
