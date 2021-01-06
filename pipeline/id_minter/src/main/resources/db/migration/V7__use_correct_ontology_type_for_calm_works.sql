USE ${database};

DELETE FROM ${tableName} WHERE OntologyType="Work" AND SourceSystem="calm-record-id";
UPDATE ${tableName} SET OntologyType="Work" WHERE SourceSystem="calm-record-id";
