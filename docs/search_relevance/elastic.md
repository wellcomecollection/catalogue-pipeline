# Elastic @ Wellcome Collection

## Who we are and what we’re doing

At the Wellcome Collection, a part of the Wellcome Trust, we are aiming to maximise the Trust’s impact on human health by understanding the social and cultural contexts of science and health.

[The platform team](https://github.com/wellcometrust/platform) are building services with the aim to [preserve our collections](https://github.com/wellcometrust/storage-service) in perpetuity, as well as make those services accessible via [APIs](https://developers.wellcomecollection.org/catalogue), [a website](https://wellcomecollection.org/works), and making the [data available to others](https://developers.wellcomecollection.org/datasets).

After setting up an [ETL](https://en.wikipedia.org/wiki/Extract,_transform,_load) [pipeline ending in Elasticsearch](https://www.elastic.co/elasticon/tour/2019/london/improving-search-at-wellcome-collection), our next challenge was to find items within the collection a person was looking for.

## Finding what people are looking for

We started with a set of known behaviours based on how people use our previous search app [WHAT DO WE CALL THIS].
Some examples of these

- Searching for a specific book they would like to see in the library
- Searching for {Subject/Genre}
- [The crazy cats of Louis Wain](https://wellcomecollection.org/works?query=Louis%20Wain)
- Searching for [dancing skeletons](https://wellcomecollection.org/works?query=dancing%20skeletons)

The next process was to create a set of our researcher’s intentions and expectations. We could then easily think about how we might create Elasticsearch queries that would satisfy these expectations.

While our data is structured, it is not currently highly structured. For instance - from the source systems we can transform certain fields into our “contributors” field. The structure of the source data was often free text, and thus inferring anything as an identifier for that specific contributor would be impossible. [BETTER EXPLANATION]

To this end we would have to rely mainly on Elasticsearch’s powerful text analysis queries rather than filters.

[CLUNKY FLOW ☝️]

Each expectation is written as a separate query making it easy to test and reason about. The powerful [match query](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-match-query.html) was used in most cases. For example - knowing that someone might be searching for a specific title, which we know is stored in the “title” and “alternativeTitles” fields. We also knew that a person might be searching from memory, and might get it slightly wrong, especially spelling as some titles are in archain languages. This could easily be expressed as:

```json
{
  "query": {
    "multi_match": {
      "query": "Die Radioaktivität",
      "fields": ["title", "alternativeTitles"], // <-- they might be searching for either
      "minimum_should_match": "80%", // <-- Might not get it completely correct
      "operator": "and", // <-- Order in a title is important
      "fuzziness": "AUTO" // <-- Allows for typos
    }
  }
}
```

## Finding things people might not be looking for (exploration)

Talk about image similarity stuff (Harrison’s nice use of Elastic’s relevance to do this)

## Measurement / Getting it right

Using website, segment, kinesis and elastic
Not to do with thinking we’ve done it, but measuring
Kibana for data scientists / analysts / dev

## Hierarchical data

Consultation, simple solution

## Coming soon

Relevance more focussed on exploration (images)
Highly structured data from entity focused indexes
Measuring specific queries
