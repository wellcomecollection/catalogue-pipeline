# Collecting data

Collecting feedback and data on how our services are being used helps iterate and improve them over time.

While this insight from behavioural data is valuable, we don't believe that bigger data is necessarily better. Our philosophy is it would be foolish to start collecting data without first establishing which questions we wanted to answer, and wrong to collect data that we don't need. For example, we see no need to personalise users' search results so our search logs are kept entirely anonymous.

We restrict the data we collect only answer specific questions that we have. This allows us to iterate quickly while limiting risks to the people using our services.

What we track is primarily split into two interactions

* What has a person searched for
* How have the interacted with the results

Examples of the data we store for these are

```javascript
// A search request
{
  "event": "Search",
  "anonymousId": "e1f51e69-d17e-4c06-a93f-7e280910b534", // This is an anonymous ID created when a session starts, and is used across all interactions of this session
  "timestamp": "2020-05-18T09:39:18.544Z",
  "network": "Staff", // If the interaction was on a staff network, we tag it with this
  "toggles": ["10-per-page"], // A list of A/B tests a person is in
  "query": { // This information is what was requested from the API
    "page": 1,
    "production.dates.from": "1900",
    "production.dates.to": "1950",
    "query": "Zodiac sign gemini",
    "format": ["books", "manuscripts", "images"]
  }
}

// Search result selected
{
  "event": "Search result selected",
  "anonymousId": "2104fa2d-59d3-423d-8958-3f254bc2bf62",
  "timestamp": "2020-05-18T09:39:23.529Z",
  "network": null,
  "toggles": ["availableOnline:false"],
  "query": { // The API request for the results
    "page": 1,
    "production.dates.from": "2000",
    "production.dates.to": "2010",
    "query": "Nurse",
    "format": ["journals"]
  },
  "data": { // Extra data about the interaction and work that was selected
    "id": "mruzf9kx",
    "position": 13,
    "resultIdentifiers": [
      "L0043772",
      "Museum No A96087"
    ],
    "resultSubjects": [
      "Nurse"
    ],
    "resultFormat": "Digital Images",
  }
}
```

## Identification and anonymisation

We store no personably identifiable information with each interaction collected.

We do store if an request was made from within Wellcome's network.

We label each interaction with an [anonymous ID from on Segment](https://segment.com/docs/connections/spec/identify/#anonymous-id).

## Storage

Data is collected on the frontend via [Segment's analytics.js](https://segment.com/docs/connections/sources/catalog/libraries/website/javascript/), sent to a kinesis stream, and then stored in Elasticsearch.

We currently retain anonymised data in perpetuity.

This document does not include general data collection across wellcomecollection.org, but for work on the [catalogue search](https://wellcomecollection.org/works).

