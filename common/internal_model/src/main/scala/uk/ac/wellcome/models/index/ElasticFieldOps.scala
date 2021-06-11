package uk.ac.wellcome.models.index

import com.sksamuel.elastic4s.fields.{
  ElasticField,
  FloatField,
  IntegerField,
  ObjectField,
  TokenCountField
}

trait ElasticFieldOps {

  /** Sometime around 7.11.x, Elastic4s removed FieldDefinition in favour of ElasticField,
    * which also lost a whole bunch of useful helpers from the DSL.
    *
    * These classes are meant to replicate some of the missing functionality, with the
    * hope that they'll be re-added in some future version of Elastic4s, and we can remove
    * these helpers as a no-op.
    */
  implicit class ObjectFieldsOps(of: ObjectField) {
    def fields(fields: ElasticField*): ObjectField =
      of.copy(properties = fields)

    def withDynamic(dynamic: String): ObjectField =
      of.copy(dynamic = Some(dynamic))
  }

  implicit class TokenCountFieldOps(tcf: TokenCountField) {
    def withAnalyzer(analyzer: String): TokenCountField =
      tcf.copy(analyzer = Some(analyzer))
  }

  implicit class IntegerFieldOps(intField: IntegerField) {
    def withIndex(index: Boolean): IntegerField =
      intField.copy(index = Some(index))
  }

  implicit class FloatFieldOps(floatField: FloatField) {
    def withIndex(index: Boolean): FloatField =
      floatField.copy(index = Some(index))
  }
}
