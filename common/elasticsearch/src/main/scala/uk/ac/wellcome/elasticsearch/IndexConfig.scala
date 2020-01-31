package uk.ac.wellcome.elasticsearch

import com.sksamuel.elastic4s.requests.indexes.CreateIndexRequest

trait IndexConfig {
  val create: CreateIndexRequest
}
