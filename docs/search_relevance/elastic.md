# Elastic @ Wellcome Collection

## Who we are and what we’re doing

[Wellcome Collection](https://wellcomecollection.org/) is a free museum and library that aims to challenge how we all think and feel about health.  
We're Wellcome Collection's platform team - we build digital services to preserve the collections in perpetuity, and make those collections accessible to people everywhere. The collection data we present is all licensed as openly as possible, and we make all of our code, our plans and our processes available on [github](https://github.com/wellcomecollection/).

We've spent the last few years building ETL pipelines to draw data out of the systems used by museum cataloguers and librarians to document our collection, and present that data in a JSON format which makes sense for our website and external developers (You can use our APIs to build whatever you want! Check out [developers.wellcomecollection.org](developers.wellcomecollection.org)).

The last stage in those pipelines is an Elasticsearch index, which we use to store a cleaned and enriched version of our collections data for public consumption. We use elasticsearch indexes in our [collections search](https://wellcomecollection.org/works) to match researchers' queries to relevant and interesting material.

In 2019, we gave [a talk at Elastic{ON}](https://www.elastic.co/elasticon/tour/2019/london/improving-search-at-wellcome-collection) about why elastic has been great for us. In this article we'll dig further into the technical process we use to develop and optimise queries, and how a multi-faceted approach to user research helps us match the shape of our queries to our users' intentions when they search.

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
