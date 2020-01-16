---
description: >-
  Some guidance for what you can do with the catalogue's APM (Application
  Performance Monitoring).
---

# APM

Some things you can track with APM:

* Errors \(with nice stack traces\). You can see an incident \(elevated 500s rate\) that we had [here](https://logging.wellcomecollection.org/app/apm#/services/catalogue_api-prod/errors?rangeFrom=2020-01-07T14:36:28.132Z&rangeTo=2020-01-10T14:36:31.106Z&refreshPaused=true&refreshInterval=0). There's an alerting tool \(watcher\) which can take a Slack web hook - we should probably use this!
* JVM stats - good for discovering memory leaks and also fun for garbage collection enthusiasts. [An example](https://logging.wellcomecollection.org/app/apm#/services/catalogue_api-prod/nodes/da16148c5d6729d7f4c0dbd08ca8f3924a3185f60daeba0e8382c5ba42f942b8/metrics?rangeFrom=now-1d&rangeTo=now&refreshPaused=true&refreshInterval=0).

The meat of APM is transaction monitoring: for us, that's monitoring the performance of endpoints. For example, for `/works` we can see [quite a lot of data.](https://logging.wellcomecollection.org/app/apm#/services/catalogue_api-prod/transactions/view?rangeFrom=2020-01-15T14:39:31.912Z&rangeTo=2020-01-16T14:39:24.817Z&refreshPaused=true&refreshInterval=0&traceId=5805866215b860a087862ba6d7089850&transactionId=851c80674e3f8e16&transactionName=GET%20%2Fworks&transactionType=request)

* The average duration of a request is about 50ms
* The 99th percentile is usually around 150ms, but is quite noisy
* We have some outlier requests where it looks like the API stalls, and/or the network connection to elastic was very slow - might be worth looking into these!

All APM data is stored in Elastic and we can do our own analyses - [here's a dashboard ](https://logging.wellcomecollection.org/app/kibana#/dashboard/4b6d1620-3397-11ea-a503-3bdf08d327aa?_g=%28refreshInterval%3A%28pause%3A!t%2Cvalue%3A0%29%2Ctime%3A%28from%3Anow-1w%2Cto%3Anow%29%29)for comparing the performance of aggregations and "normal" queries.

