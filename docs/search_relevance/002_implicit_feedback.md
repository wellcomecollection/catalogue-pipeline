# Test 2

## Candidates

Based on the conclusions from test 1, we've tweaked a few of the parameters and assembled three new candidates which we think will be effective improvements to the standard algorithm.

We'd like to test this on the live site with people outside Wellcome, possibly collecting explicit feedback from users who opt in (or don't opt in! The site is in beta after all). We'll also be able to evaluate the standard algorithm alonside the new candidates.

**minimum should match**

```
{
  "query": {
    "multi_match" : {
      "type": "cross_fields",
      "query" : "oil painting of urine",
      "fields" : ["*"],
      "minimum_should_match": "60%"
    }
  }
}
```

**boost**

```
{
  "query": {
    "multi_match" : {
      "type": "cross_fields",
      "query" : "oil painting of urine",
      "fields" : ["*", “title^9”, “subjects*^8", “genres*^8”, “description*^5", “contributors*^2”]
    }
  }
}
```

**minimum should match + boost**

```
{
  "query": {
    "multi_match" : {
      "type": "cross_fields",
      "query" : "oil painting of urine",
      "fields" : ["*", “title^9”, “subjects*^8", “genres*^8”, “description*^5", “contributors*^2”]
      "minimum_should_match": "60%"
    }
  }
}
```

## Results

minimum should match + boost did best! Default query changed accordingly
