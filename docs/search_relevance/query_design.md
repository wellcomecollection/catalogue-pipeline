# Query design

Each intention is mapped to a query within Elastic.

We then boost these to sort the results in relation to people's expectations and priorities.

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

## Intentions & Expectations

### Shingled titles

| Status | Query name | Ranking evaluation test |
| :--- | :--- | :--- |
| TODO | Shingles | TBD |

#### Intentions

Searching for works with structurally important features in the query. Most useful when looking for specific titles, where the order of tokens matters most.

#### Data features

* `data.title`
* `data.alternativeTitle`

#### Expectations

* Searching for an exact title should show that title at the top of the list
* Occurrences of ordered tokens matching the query should appear before the matches which occur in a different order 

#### Examples

* `east london` - [https://wellcomecollection.org/works/ufw89pqr](https://wellcomecollection.org/works/ufw89pqr) above [https://wellcomecollection.org/works/pabxvfqu](https://wellcomecollection.org/works/pabxvfqu)
* `The ocean as a health resort : a practical handbook of the sea for the use of tourists and health-seekers`- [https://wellcomecollection.org/works/uxxaqdkg](https://wellcomecollection.org/works/uxxaqdkg) at the top of the list

### Titles

| Status | Query name | Ranking evaluation test |
| :--- | :--- | :--- |
| Stable | TitleQuery | TBD |

#### Intentions

Searching for a work by its title.

#### Data features

* `data.title`
* `data.alternativeTitles`

#### Expectations

* When the exact title is searched for, it is the first result
* If it is a partial match of the title, it is the first result

#### Examples

TBD

#### What's next

* How to handle fuzziness?

### IDs

| Status | Query name | Ranking evaluation test |
| :--- | :--- | :--- |
| TODO | IdQuery | TBD |

#### Intentions

Searching for a work based on local and external identifiers e.g. Catalogue API IDs, Sierra IDs etc.

#### Data features

* `canonicalIds`
* `sourceIdentifiers`
* `otherIdentifiers`

#### Expectations

* Searching for _an_ identifier, I get _the_ result back
* Searching for a list of identifiers, I get all the results back
* Searches should be case insensitive
* If the search query contains and ID and other input, we should match

  the ID and terms with the the ID match at the top of the list.

#### Examples

* `V1234567`
* `V1234567 i1234567 aTrf569`

### Contributors

| Status | Query name | Ranking evaluation test |
| :--- | :--- | :--- |
| Testing | ContributorsQuery | TBD |

#### Intentions

Searching for works that have certain subjects associated with it.

#### Data features

* `contributors.label`

#### Expectations

* Searching for the exact name of a subject, works they have

  contributed towards are first results

#### Examples

TBD

### Subjects

| Status | Query name | Ranking evaluation test |
| :--- | :--- | :--- |
| Testing | SubjectsQuery | TBD |

#### Intentions

Searching for works that have certain subjects associated with it.

#### Data features

* `subjects.label`

#### Expectations

* Searching for the exact name of a subject, works they have

  contributed towards are first results

#### Examples

TBD

### Genres

| Status | Query name | Ranking evaluation test |
| :--- | :--- | :--- |
| Testing | GenresQuery | TBD |

#### Intentions

Searching for works that have certain genres associated with it.

#### Data features

* `genres.label`

#### Expectations

* Searching for the exact name of a genre, works they have

  contributed towards are first results

#### Examples

TBD

### General

| Status | Query name | Ranking evaluation test |
| :--- | :--- | :--- |
| Testing | GeneralQuery | TBD |

#### Intentions

Searching the catalogue for general information

#### Data features

* `title`
* `alternativeTitles`
* `physicalDescription`
* `language`
* `edition`
* `physicalDescription`
* `subjects.label`
* `genres.label`
* `contributors.label`
* `description`

#### Expectations

* Relevant and interesting results are returned in order of relevance

  and interest

#### Examples

TBD

