## Elastic @ Wellcome Collection

# Who we are and what we’re doing

[Wellcome Collection](https://wellcomecollection.org/) is a free museum and library that aims to challenge how we all think and feel about health.  
We're Wellcome Collection's platform team - we build digital services to preserve the collections in perpetuity, and make those collections accessible to people everywhere. The collection data we present is all licensed as openly as possible, and we make all of our code, our plans and our processes available on [github](https://github.com/wellcomecollection/).

We've spent the last few years building ETL pipelines to draw data out of the systems used by museum cataloguers and librarians to document our collection, and present that data in a JSON format which makes sense for our website and external developers (You can use our APIs to build whatever you want! Check out [developers.wellcomecollection.org](developers.wellcomecollection.org)).

The last stage in those pipelines is an Elasticsearch index, which we use to store a cleaned and enriched version of our collections data for public consumption. We use elasticsearch indexes in our [collections search](https://wellcomecollection.org/works) to match researchers' queries to relevant and interesting material.

In 2019, we gave [a talk at Elastic{ON}](https://www.elastic.co/elasticon/tour/2019/london/improving-search-at-wellcome-collection) about why elastic has been great for us. In this article we'll dig further into the technical process we use to develop and optimise queries, and how a multi-faceted approach to user research helps us match the shape of our queries to our users' intentions when they search.

## What are our users trying to do?

We started with an inhereted set of users' search behaviours and intentions from the previous version of our site, [wellcomelibrary.org](https://wellcomelibrary.org).

A few of the most prominent behaviours which carried over to the new site were:

- Searching for a specific work with a known title, like ["A popular history of astronomy during the nineteenth century"](https://wellcomecollection.org/works?query=A%20popular%20history%20of%20astronomy%20during%20the%20nineteenth%20century)
- Searching for a known subject, like ["alchemy"](https://wellcomecollection.org/works?query=alchemy)
- Searching for a group of items under a fuzzy umbrella term, like ["the crazy cats of Louis Wain"](https://wellcomecollection.org/works?query=the%20crazy%20cats%20of%20Louis%20Wain), or ["dancing skeletons"](https://wellcomecollection.org/works?query=dancing%20skeletons)

It was clear from the search logs that we were serving two broad classes of user:

- those searching for a specific work, where every result before the one they're looking for is a nuisance, and every result after it is irrelevant.
- those researching a topic, who are happy to leaf through every page of results to understand its scope in our collection. The order of results matters less to these users, as long as every result is plausibly related to the topic.

Meeting these intentions had to be paired with users' expectations of a single search bar which us capable of meeting all their needs. If we were going to deliver that subtle balance of precision and recall, we would have to tune our queries carefully.

## Matching user intentions

[documenting hypothesised intentions and how we meet them](https://docs.wellcomecollection.org/catalogue/search_relevance/intentions-and-expectations)

The next process was to create a set of our researcher’s intentions and expectations. We could then easily think about how we might create Elasticsearch queries that would satisfy these expectations.

While our data is structured, it is not currently highly structured. For instance - from the source systems we can transform certain fields into our “contributors” field. The structure of the source data was often free text, and thus inferring anything as an identifier for that specific contributor would be impossible. [BETTER EXPLANATION]

To this end we would have to rely mainly on Elasticsearch’s powerful text analysis queries rather than filters.

[CLUNKY FLOW ☝️]

Each expectation is written as a separate query making it easy to test and reason about. The powerful match query was used in most cases. For example - knowing that someone might be searching for a specific title, which we know is stored in the “title” and “alternativeTitles” fields. We also knew that a person might be searching from memory, and might get it slightly wrong, especially spelling as some titles are in archain languages. This could easily be expressed as:

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

# Serendipity and exploration

Talk about image similarity stuff (Harrison’s nice use of Elastic’s relevance to do this)

# Tracking user behaviour

didn't want to collect every interaction without even establishing what questions we wanted to answer
built a narrow pipe to a seperate ES cluster, pulling only what we needed.
ethically cleaner, and allows us to move faster.

Using website, segment, kinesis and elastic
Not to do with thinking we’ve done it, but measuring
Kibana for data scientists / analysts / dev

# Coming soon

Relevance more focussed on exploration (images)
Highly structured data from entity focused indexes
Measuring specific queries
