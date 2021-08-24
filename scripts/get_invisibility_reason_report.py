#!/usr/bin/env python

import collections
import json
from pprint import pprint

from elasticsearch import Elasticsearch
from elasticsearch.helpers import scan
import httpx
import tqdm

from _common import get_session, get_secret_string


if __name__ == "__main__":
    sess = get_session(role_arn="arn:aws:iam::760097843905:role/platform-developer")

    index_config = httpx.get(
        "https://api.wellcomecollection.org/catalogue/v2/_elasticConfig"
    ).json()

    pipeline_date = index_config["worksIndex"].replace("works-indexed-", "")

    # username = get_secret_string(
    #     sess,
    #     secret_id=f"elasticsearch/pipeline_storage_{pipeline_date}/read_only/es_username",
    # )
    # password = get_secret_string(
    #     sess,
    #     secret_id=f"elasticsearch/pipeline_storage_{pipeline_date}/read_only/es_password",
    # )
    # host = get_secret_string(
    #     sess, secret_id=f"elasticsearch/pipeline_storage_{pipeline_date}/public_host"
    # )
    #
    # es = Elasticsearch(f"https://{host}:9243", http_auth=(username, password))
    #
    # scanner = scan(
    #     es,
    #     index=index_config["worksIndex"],
    #     query={"query": {"bool": {"filter": [{"term": {"type": "Invisible"}}]}}, "_source": ["state", "invisibilityReasons"]},
    #     scroll="1m"
    # )
    #
    # with open(f'invisible_works_{pipeline_date}.json', 'w') as outfile:
    #     for item in tqdm.tqdm(scanner):
    #         outfile.write(json.dumps(item) + "\n")

    invisibility_reasons = collections.defaultdict(list)

    for line in open(f"invisible_works_{pipeline_date}.json"):
        work = json.loads(line)

        for reason in work["_source"]["invisibilityReasons"]:
            invisibility_reasons[(reason["type"], reason.get("message"))].append(
                work["_source"]
            )

    with open(f"invisibility_reasons_{pipeline_date}.html", "w") as outfile:
        for (reason, message), works in invisibility_reasons.items():
            if reason == "UnableToTransform" and message.startswith(
                "Calm:Suppressed level -"
            ):
                continue

            if reason == "MetsWorksAreNotVisible":
                continue

            outfile.write(f"<h1>{reason} - {message}</h1>\n\n")
            outfile.write("<ul>\n")
            for w in works:
                outfile.write(
                    f"  <li>{w['state']['sourceIdentifier']['identifierType']}/{w['state']['sourceIdentifier']['value']}"
                )
            outfile.write("</ul>\n\n")

    # with open("invisibility_reasons.json", "w") as of:
    #     of.write(json.dumps(list(invisibility_reasons.items()), indent=2, sort_keys=True))
