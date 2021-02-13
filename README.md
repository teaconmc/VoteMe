# Vote Me

## Commands

All the commands start with `/voteme`.

Moderator commands (available to ops by default):

* `/voteme list roles`
* `/voteme list categories`
* `/voteme list artifacts`
* `/voteme select <artifact>`
* `/voteme clear <category>`
* `/voteme unset <targets> <category>`
* `/voteme set <targets> <category>`
* `/voteme switch <category> on`
* `/voteme switch <category> off`
* `/voteme switch <category> unset`

Admin commands (available to nobody by default):

* `/voteme admin switch <category> on`
* `/voteme admin switch <category> off`
* `/voteme admin switch <category> unset`
* `/voteme admin rename <name>`
* `/voteme admin remove`
* `/voteme admin create <name>`

## HTTP API

The default port of http server is `19970`.

### `GET /v1/categories`

Fetch vote categories. Example response:

```json
[
  {
    "id": "voteme:general",
    "name": "General",
    "description": "General judgement of the work. The final result is 80% from players and 20% from professional judges. You can edit this category by modifying 'data/voteme/vote_categories/general.json'.",
    "vote_lists": [ 1 ]
  },
  {
    "id": "voteme:professional",
    "name": "Professional",
    "description": "Professional judgement of the work. The final result is 100% from professional judges. One of the highest and one of the lowest will be removed. You can edit this category by modifying 'data/voteme/vote_categories/professional.json'.",
    "vote_lists": [ 2 ]
  }
]
```

### `GET /v1/categories/<id>`

Fetch a particular vote category. Example response:

```json
{
  "id": "voteme:general",
  "name": "General",
  "description": "General judgement of the work. You can edit this category by loading a datapack and modifying \u0027data/voteme/vote_categories/general.json\u0027.",
  "vote_lists": [ 1 ]
}
```

### `GET /v1/artifacts`

Fetch artifacts for voting. Example response:

```json
[
  {
    "id": "888891b2-7326-42da-9ed2-a36a8f301410",
    "name": "VoteMe",
    "vote_lists": [ 1, 2 ]
  }
]
```

The artifact id is guaranteed to be a UUID.

### `GET /v1/artifacts/<id>`

Fetch an artifact for voting by its UUID. Example response:

```json
{
  "id": "888891b2-7326-42da-9ed2-a36a8f301410",
  "name": "VoteMe",
  "vote_lists": [ 1, 2 ]
}
```

### `GET /v1/vote_lists`

Fetch a collection of all the vote lists. Example response:

```json
[
  {
    "id": 1,
    "category": "voteme:general",
    "artifact": "888891b2-7326-42da-9ed2-a36a8f301410",
    "vote_stats": {
      "score": 10.0,
      "weight": 1.0,
      "counts": {
        "1": 0,
        "2": 0,
        "3": 0,
        "4": 0,
        "5": 1,
        "sum": 1,
        "effective": 1
      },
      "subgroups": [
        {
          "id": "voteme:general_players",
          "score": 10.0,
          "weight": 0.8,
          "counts": {
            "1": 0,
            "2": 0,
            "3": 0,
            "4": 0,
            "5": 1,
            "sum": 1,
            "effective": 1
          }
        },
        {
          "id": "voteme:professional_judges",
          "score": 10.0,
          "weight": 0.2,
          "counts": {
            "1": 0,
            "2": 0,
            "3": 0,
            "4": 0,
            "5": 0,
            "sum": 0,
            "effective": 0
          }
        }
      ]
    }
  },
  {
    "id": 2,
    "category": "voteme:professional",
    "artifact": "888891b2-7326-42da-9ed2-a36a8f301410",
    "vote_stats": {
      "score": 6.0,
      "weight": 1.0,
      "counts": {
        "1": 0,
        "2": 0,
        "3": 0,
        "4": 0,
        "5": 0,
        "sum": 0,
        "effective": 0
      },
      "subgroups": [
        {
          "id": "voteme:professional_judges",
          "score": 6.0,
          "weight": 1.0,
          "counts": {
            "1": 0,
            "2": 0,
            "3": 0,
            "4": 0,
            "5": 0,
            "sum": 0,
            "effective": 0
          }
        }
      ]
    }
  }
]
```

The response JSON array is guaranteed to be sorted by ids.

The ids are guaranteed to be integers, which are positive numbers in common cases.

### `GET /v1/vote_lists/<id>`

Fetch a vote list by its integer id. Example response:

```json
{
  "id": 1,
  "category": "voteme:general",
  "artifact": "888891b2-7326-42da-9ed2-a36a8f301410",
  "vote_stats": {
    "score": 10.0,
    "weight": 1.0,
    "counts": {
      "1": 0,
      "2": 0,
      "3": 0,
      "4": 0,
      "5": 1,
      "sum": 1,
      "effective": 1
    },
    "subgroups": [
      {
        "id": "voteme:general_players",
        "score": 10.0,
        "weight": 0.8,
        "counts": {
          "1": 0,
          "2": 0,
          "3": 0,
          "4": 0,
          "5": 1,
          "sum": 1,
          "effective": 1
        }
      },
      {
        "id": "voteme:professional_judges",
        "score": 10.0,
        "weight": 0.2,
        "counts": {
          "1": 0,
          "2": 0,
          "3": 0,
          "4": 0,
          "5": 0,
          "sum": 0,
          "effective": 0
        }
      }
    ]
  }
}
```

The example response shows two different subgroups of voting statistics.

* The one is called `voteme:general_players`, with `1` vote of five stars (in which `1` is effective), and the `score` is `10.0`.
* The other is called `voteme:professional_judges`, with `0` vote (in which `0` is effective), and the score is undefined, then following the final `score`, `10.0`.

The final `score` is the weighted average score of voting statistics, so in this example it is `(0.8 * 10.0 + 0.2 * 10.0) / (0.8 + 0.2) = 10.0`.

### `GET /v1/roles`

Fetch a collection of different roles for voting. Example response:

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

Fetch a particular role for voting by its id. Example response:

```json
{
  "id": "voteme:general_players",
  "name": "Players"
}
```
