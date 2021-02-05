# Vote Me

## HTTP API

The default port of http server is `19970`.

### `GET /v1/categories`

Fetch vote categories. Example Response:

```json
[
  {
    "id": "voteme:general",
    "name": "General",
    "description": "General judgement of the work. You can edit this category by loading a datapack and modifying \u0027data/voteme/vote_categories/general.json\u0027.",
    "vote_lists": [ 1 ]
  },
  {
    "id": "voteme:professional",
    "name": "Professional",
    "description": "Professional judgement of the work. You can edit this category by loading a datapack and modifying \u0027data/voteme/vote_categories/professional.json\u0027.",
    "vote_lists": [ 2 ]
  }
]
```

### `GET /v1/categories/<id>`

Fetch a particular vote category. Example Response:

```json
{
  "id": "voteme:general",
  "name": "General",
  "description": "General judgement of the work. You can edit this category by loading a datapack and modifying \u0027data/voteme/vote_categories/general.json\u0027.",
  "vote_lists": [ 1 ]
}
```

### `GET /v1/artifacts`

Fetch artifacts for voting. Example Response:

```json
[
  {
    "id": "8898dd9a-23cd-4f5f-80db-f66a32fd5e66",
    "name": "VoteMe",
    "vote_lists": [ 1, 2 ]
  }
]
```

The artifact id is guaranteed to be a UUID.

### `GET /v1/artifacts/<id>`

Fetch an artifact for voting by its UUID. Example Response:

```json
{
  "id": "8898dd9a-23cd-4f5f-80db-f66a32fd5e66",
  "name": "VoteMe",
  "vote_lists": [ 1, 2 ]
}
```

### `GET /v1/vote_lists`

Fetch a collection of all the vote lists. Example Response:

```json
[
  {
    "id": 1,
    "category": "voteme:general",
    "artifact": "8898dd9a-23cd-4f5f-80db-f66a32fd5e66",
    "vote_counts": [
      {
        "role": "voteme:general_players",
        "1": 0,
        "2": 0,
        "3": 0,
        "4": 0,
        "5": 1,
        "sum": 1,
        "effective": 1,
        "weight": 1.0,
        "score": 10.0
      },
      {
        "role": "voteme:professional_judges",
        "1": 0,
        "2": 0,
        "3": 0,
        "4": 0,
        "5": 0,
        "sum": 0,
        "effective": 0,
        "weight": 2.0,
        "score": 6.0
      }
    ],
    "final_score": 10.0
  },
  {
    "id": 2,
    "category": "voteme:professional",
    "artifact": "8898dd9a-23cd-4f5f-80db-f66a32fd5e66",
    "vote_counts": [
      {
        "role": "voteme:professional_judges",
        "1": 0,
        "2": 0,
        "3": 0,
        "4": 0,
        "5": 0,
        "sum": 0,
        "effective": 0,
        "weight": 1.0,
        "score": 6.0
      }
    ],
    "final_score": 6.0
  }
]
```

The response JSON array is guaranteed to be sorted by ids.

The ids are guaranteed to be integers, which are positive numbers in common cases.

### `GET /v1/vote_lists/<id>`

Fetch a vote list by its integer id. Example Response:

```json
{
  "id": 1,
  "category": "voteme:general",
  "artifact": "8898dd9a-23cd-4f5f-80db-f66a32fd5e66",
  "vote_counts": [
    {
      "role": "voteme:general_players",
      "1": 0,
      "2": 0,
      "3": 0,
      "4": 0,
      "5": 1,
      "sum": 1,
      "effective": 1,
      "weight": 1.0,
      "score": 10.0
    },
    {
      "role": "voteme:professional_judges",
      "1": 0,
      "2": 0,
      "3": 0,
      "4": 0,
      "5": 0,
      "sum": 0,
      "effective": 0,
      "weight": 2.0,
      "score": 6.0
    }
  ],
  "final_score": 10.0
}
```

The example response shows two different types of voting statistics.

* The one is from `voteme:general_players`, with `1` vote of five stars (in which `1` is effective), and the score is `10.0`.
* The other is from `voteme:professional_judges`, with `0` vote (in which `0` is effective), and the score is `6.0` (default value).

The final score is the weighted average score of voting statistics whose weight is calculated by `effective * weight`. So in this example it is `(1 * 1.0 * 10.0 + 0 * 2.0 * 6.0) / (1 * 1.0 + 0 * 2.0) = 10.0`

### `GET /v1/roles`

Fetch a collection of different roles for voting. Example Response:

```json
[
  {
    "id": "voteme:general_players",
    "name": "Players"
  },
  {
    "id": "voteme:professional_judges",
    "name": "Professional Judges"
  }
]
```

### `GET /v1/roles/<id>`

Fetch a particular role for voting by its id. Example Response:

```json
{
  "id": "voteme:general_players",
  "name": "Players"
}
```
