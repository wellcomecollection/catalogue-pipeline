package uk.ac.wellcome.elasticsearch

import com.sksamuel.elastic4s.requests.indexes.CreateIndexRequest

trait IndexConfig {
  def create(name: String): CreateIndexRequest
}
