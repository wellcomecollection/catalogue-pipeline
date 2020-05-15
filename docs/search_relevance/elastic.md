# Optimising queries for user intent at Wellcome Collection

## Who we are and what we’re doing

[Wellcome Collection](https://wellcomecollection.org/) is a free museum and library that aims to challenge how we all think and feel about health.  
We're Wellcome Collection's platform team - we build digital services to preserve the collections in perpetuity, and make those collections accessible to people everywhere. The collection data we present is all licensed as openly as possible, and we make all of our code, our plans and our processes available on [github](https://github.com/wellcomecollection/).

We've spent the last few years building ETL pipelines to draw data out of the systems used by museum cataloguers and librarians to document our collection, and present that data in a JSON format which makes sense for our website and external developers (You can use our APIs to build whatever you want! Check out [developers.wellcomecollection.org](developers.wellcomecollection.org)).

The last stage in those pipelines is an Elasticsearch index, which we use to store a cleaned and enriched version of our collections data for public consumption. We use elasticsearch indexes in our [collections search](https://wellcomecollection.org/works) to match researchers' queries to relevant and interesting material.

In 2019, we gave [a talk at Elastic{ON}](https://www.elastic.co/elasticon/tour/2019/london/improving-search-at-wellcome-collection) about why elastic has been great for us. In this article we'll dig further into the technical process we use to develop and optimise queries, and how a multi-faceted approach to user research helps us match the shape of our queries to our users' intentions when they search.

## What are our users trying to do?

We started with an inherited set of users' search behaviours and intentions from the previous version of our site, [wellcomelibrary.org](https://wellcomelibrary.org).

Some of the most prominent behaviours which carried over to the new site were:

- Searching for a specific work with a known title, like ["A popular history of astronomy during the nineteenth century"](https://wellcomecollection.org/works?query=A%20popular%20history%20of%20astronomy%20during%20the%20nineteenth%20century)
- Searching for a known subject, like ["alchemy"](https://wellcomecollection.org/works?query=alchemy)
- Searching for a group of items under a fuzzy umbrella term, like ["the crazy cats of Louis Wain"](https://wellcomecollection.org/works?query=the%20crazy%20cats%20of%20Louis%20Wain), or ["dancing skeletons"](https://wellcomecollection.org/works?query=dancing%20skeletons)

It was clear from the search logs that we were serving two broad classes of user:

- those searching for a specific work, where every result before the one they're looking for is a nuisance, and every result after it is irrelevant.
- those researching a topic, who are happy to leaf through every page of results to understand its scope in our collection. The order of results matters less to these users, as long as every result is plausibly related to the topic.

Meeting these intentions had to be paired with users' expectations of a single search bar which us capable of meeting all their needs. If we were going to deliver that subtle balance of precision and recall, we would have to tune our queries carefully.

## Tracking user behaviour

Collecting data is a necessary part of optimising our queries. While the insight from behavioural data is valuable, we don't believe that bigger data is necessarily better - we think it would be foolish to start collecting data without first establishing which questions we wanted to answer, and wrong to collect data that we don't need. For example, we see no need to personlalise users' results so our search logs are kept entirely anonymous.

By restricting the data we collect to just enough to answer specific questions, we're able to iterate quickly while limiting risks to our users.

Having established a set of questions about how search was being used and was performing, we set up [Segment](https://segment.com/) and [Kinesis](https://aws.amazon.com/kinesis/) to pipe a narrow set of actions on our website into a elastic cluster specifically for reporting, separate from our catalogue indexes.

Running [Kibana](https://www.elastic.co/kibana) on top of those indexes allows our team of developers, analysts, and data scientists to build and access dashboards, keeping track of key metrics on which we make decisions about how our queries will be tuned.

## User research

Search logs alone aren't enough to get a clear picture of how and why researchers behave the way they do. We combine the anonymous aggregate behavioural data with face-to-face interviews with researchers to build the clearest possible picture of how our users expect search to work, and to build the most direct set of intentions to match.

## Matching user intentions

With a set of questions and a way of answering them in place, we could really start breaking apart user intentions, and [explicitly documenting our hypotheses](https://docs.wellcomecollection.org/catalogue/search_relevance/intentions-and-expectations) about how people expect search to work.

We use a layered approach to constructing queries, stacking a set of highly boosted `bool_query`s on top of a broad, minimally boosted base query. The base query ensures that we always return _something_ for a sensible query, and each layer in the stack of `bool_query`s addresses a specific intention.

In our case, `match` queries have become most commonly used layer in that stack because of their versatility.

The advantage of this layered approach is most clearly demonstrated when we see a user displaying one clear intention, eg exactly searching for a work's title.  
We match the intention by running a match query against our `"title"` and `"alternativeTitle"` fields. We also know that a user might not express the match perfectly when searching from memory, especially as we hold works from a wide range of languages and time-periods. This intention can be expressed as:

```json
{
  "query": {
    "multi_match": {
      "query": "Die Radioaktivität",
      "operator": "and",
      "fields": ["title", "alternativeTitles"], // <-- Could be searching for either
      "minimum_should_match": "80%", // <-- Might not get it completely correct
      "fuzziness": "AUTO" // <-- Allows for typos
    }
  }
}
```

<!-- I think this json should probably be beefed up to show a base query and a dummy second bool query -->

As well as matching the base query, if their query matches one of our intention blocks, we unambiguously boost the block of matching results to the top of the results list.  
If no clear intentions are matched, we still return a generic set of results. At the same time, their query is logged so that we have the opportunity to spot patterns of matching behaviour in the future.  
By keeping each block deliberately narrow and matched to an unambiguous intention, we minimise the chance that they clash or interfere with one another.  
The layers are also easily refined and extended - there's no reason why we can't add a subsequent block to only match exact titles, to serve a user who intends to look for a copy-and-pasted title.

This modular approach allows us to optimise each intention separately. By testing an individual addition or change to our stack of queries, we can incrementally test and improve performance for each intention. If we see no improvement or a reduction in performance, the candidate query is also easily removed.

It's important to note that our queries aren't perfect yet, and that's the point. This approach allows us to continuously refine a more perfect query.

## What comes next?

One aspect of the intention-matching approach which we haven't yet taken advantage of is the ability to log which results matched which sub-queries (if any), and therefore which sub-queries are most effective. Our current metrics are aggregates of all sub-queries, but being able to observe and tune every part of the user-query-result loop is hugely desirable.

## Things we should do separate posts on

- hierarchical search, and matching archives like paths
- serendipitous exploration and image similarity in elasticsearch
- Relevance more focussed on exploration (images)?
- Highly structured data from entity focused indexes
