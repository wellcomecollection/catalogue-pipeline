package weco.pipeline.transformer.sierra.transformers

import grizzled.slf4j.Logging
import weco.pipeline.transformer.sierra.data.SierraMarcDataConversions

trait SierraAbstractConcepts
    extends Logging
    with SierraHasRecordControlNumber
    with SierraMarcDataConversions
