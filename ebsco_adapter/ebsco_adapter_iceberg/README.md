Download and extract EBSCO data, populating an Iceberg table with it.

EBSCO data is in MARC XML format, provided as a set of `<record>` elements inside a `<collection>` documentElement.

For each record, resulting Iceberg table contains the XML, stored in the `content` field, and the identifier, of the
record,
derived from the `<controlfield tag="001">` element

Subsequent updates will look for matching records using that same id and:

* Insert any new records not found in the existing table
* Delete (blank-out) any records not found in the incoming data
* Change any records that are different between the two.

Each update can be identified by its changeset identifier, which is returned from this process.