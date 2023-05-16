# es_index module

This module creates an Elasticsearch index based on input JSON files.

Index creation will fail if the combination of mappings an analysers is invalid - e.g. if a mapping specifies an analyser that does not exist.

The mappings and analysers file paths are derived from the names given in the analyzers_name and mappings_name variables.  analyzers_name is optional and will be copied from mappings_name if omitted.
