# Given the identifier and source system name, return a GET request that can be
# used in the Elasticsearch console to fetch the record from the works-source index.

# It's basically just URL Encoding, but it can be a bit of a faff compared to ids in other indices
import sys
from urllib.parse import quote_plus

date = sys.argv[1]
source_system = sys.argv[2]
work_id = sys.argv[3]
doc_id = quote_plus(f"Work[{source_system}/{work_id.lower()}]")
print(f"GET works-source-{date}/_doc/{doc_id}")
