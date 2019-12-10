# Test 1

## Candidates

Four initial candidates were assembled to get a feel for the effects of each lever we can pull. They were tested explicitly with internal users. Using the json query format, they look like this:

**just boosting**

```text
{
  "query": {
    "multi_match" : {
      "query" : "oil painting of urine",
      "fields" : ["*", "subjects*^4", "genres*^4", "title^3"],
      "type": "cross_fields"
    }
  }
}
```

**broader boosting**

```text
{
  "query": {
    "multi_match" : {
      "query" : "oil painting of urine",
      "fields" : ["*", "subjects*^8", "genres*^8", "title^5", "description*^2", "lettering*^2", "contributors*^2"],
      "type": "cross_fields"
    }
  }
}
```

**slop**

```text
{
  "query": {
    "multi_match" : {
      "query" : "oil painting of urine",
      "fields" : ["*"],
      "type": "cross_fields",
      "slop": 3
    }
  }
}
```

**minimum should match**

```text
{
  "query": {
    "multi_match" : {
      "query" : "oil painting of urine",
      "fields" : ["*"],
      "type": "cross_fields",
      "minimum_should_match": "70%"
    }
  }
}
```

## Results

| Query | NDCG | Pstrict | Ploose | Ppermissive |
| :--- | :--- | :--- | :--- | :--- |
| justBoost | 0.874 | 0.366 | 0.491 | **0.893** |
| broaderBoost | 0.858 | 0.362 | 0.500 | 0.839 |
| slop | **0.908** | **0.509** | **0.679** | 0.885 |
| minimumShouldMatch | 0.883 | 0.426 | 0.561 | 0.861 |

## Conclusions

Stupidly, we didn't measure the performance of the standard algorithm alongside the candidates below so we can't immediately swap the existing one out for a better performing one yet. However, the candidates weren't intended to be an immediate improvement \(more a test of the kind of tweaks we can make\) so we're happy to save the rpduction changes for a later date.

The difference between `justboost` and `broaderBoost` shows that we are having an effect by tweaking these parameters. We'll be carrying over some of these insights into the next round of testing.

`slop` is an interesting case - while its values are high across all of the metrics, a few of the participants voiced frustration that there were often very few results in the list. We only take ratings for the top 5 results, but the difference was stark between the current algorithm which often returns 1000s of results and the `slop` results which often returned fewer than 10. `slop` effectively gamed the system by harshly restricting the results list; while all of the results were highly relevant, there were lots of works which should have been counted as relevant but were left out. To users who know the collection well, this is an even more frustrating problem than being presented with hundreds of pages of results to sift through.

`minimum_should_match` showed similar behaviour, but the effects were softer. Given that we were using `slop` to emulate an index-side change which _will_ eventually be made, and that `minimum_should_match` is a more tunable parameter, we've decided to use a combination of `boost` and `minimum_should_match` to construct the next set of candidate queries.

