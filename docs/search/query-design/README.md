# Query design

Search algorithms use different tools or "queries" to return results.  These tools can be combined and ordered in relation to people's expectations and priorities.

e.g.

Someone is searching for `Honor Fell`. This would match both intentions of the works with the contributor of `Honor Fell` and works with `Honor Fell` in the title.

We would then boost the contributor query by `2000` and the title query by `1000`

This would surface works by `Honor Fell` first, and then works with `Honor Fell` in the title.

Each query has:

* **Intentions:** What a person is trying to achieve with their search
* **Data features:** Parts of the data we think are relevant
* **Status:** Where we are with developing this query
  * **TODO:** We know it's something we need to do
  * **Testing:** The initial query has been created and is running as a

    test

  * **Stable:** The current implementation of the query meets the

    expectations and has been measured in the world as doing so.
