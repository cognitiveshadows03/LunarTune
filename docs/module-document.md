# Loaded modules

A module is a JSON document that tells LunarTune how to ask a service for a
stream. It runs no code: everything it can do is one of the shapes below, with
its own addresses in them. Modules you install appear in **Settings → Sources**
next to the ones that ship with the app, take part in the same priority order,
and fall back to YouTube Music the same way.

Two rules the app enforces, and why:

- **http and https only.** A module cannot name a `file:`, `content:` or `intent:`
  address, so it cannot be used to read the device's own storage.
- **Nothing is evaluated.** Values are substituted into templates and
  percent-encoded, so a track title containing `&` or `/` cannot break out of the
  parameter it was placed in.

## Installing

**Settings → Sources → Loaded modules → From an address** takes a URL;
**Paste a document** takes the text directly. After installing, switch the module
on in the list above — a new module starts disabled.

## The document

```json
{
  "id": "example.saavn",
  "name": "Example Saavn",
  "version": "1.0",
  "description": "Lossless where the catalogue has it",
  "author": "you",
  "homepage": "https://example.com",

  "capabilities": {
    "stream": {
      "search": {
        "url": "https://api.example.com/search?q={title}&artist={artist}&limit=10",
        "method": "GET",
        "headers": { "X-Api-Key": "{fields.key}" }
      },
      "stream": {
        "url": "https://api.example.com/song/{match.id}/stream?quality={picks.quality}",
        "headers": { "Referer": "https://example.com/" }
      },
      "match": {
        "resultsPath": "data.results",
        "idPath": "id",
        "titlePath": "title",
        "artistPath": "artist",
        "albumPath": "album",
        "durationPath": "duration"
      },
      "extract": {
        "urlPath": "data.media.url",
        "codecPath": "data.media.codec",
        "bitratePath": "data.media.bitrate",
        "format": "progressive"
      },
      "codec": "AAC",
      "quality": "320 kbps"
    }
  },

  "choices": [
    {
      "id": "quality",
      "label": "Quality",
      "options": [
        { "id": "hi", "label": "High", "description": "320 kbps" },
        { "id": "lo", "label": "Low", "description": "96 kbps" }
      ]
    }
  ],

  "fields": [
    {
      "id": "key",
      "label": "API key",
      "kind": "password",
      "default": "",
      "description": "From your account page."
    }
  ]
}
```

### What happens on a resolve

1. `search.url` is filled in and requested.
2. `match.resultsPath` is walked to the list of candidates. Each is tested
   against the track: the title must contain what was asked for, and a duration
   that both sides know must be within the matching tolerance (**Settings →
   Sources → Track matching**). Candidates that fail are dropped; among the rest
   the closest title and artist wins.
3. The matched record's `id` becomes `{match.id}`.
4. `stream.url` is filled in and requested.
5. `extract.urlPath` is read for the playable address.

### Placeholders

| In `search` | Meaning |
|---|---|
| `{title}` | the song title |
| `{artist}` | the first artist |
| `{artists}` | every artist, space separated |
| `{album}` | the album, empty if unknown |
| `{duration}` | length in whole seconds |
| `{isrc}` | the ISRC, empty if unknown |

| In `stream` | Meaning |
|---|---|
| `{match.id}` | the id of the record the match step chose |
| `{match.title}`, `{match.artist}`, `{match.album}` | that record's own fields |

| Anywhere | Meaning |
|---|---|
| `{picks.<choiceId>}` | the picked option's id |
| `{picks.<choiceId>.label}` | the picked option's label |
| `{fields.<fieldId>}` | a setting's value, or its default |

A placeholder with no value becomes empty rather than being left in the URL.
Values are percent-encoded where they land in an address, so they cannot break
the query.

### Paths

`data.results`, `data.media.url`, `data.songs[0].id`, `data.items[*].id`. A path
that lands on an array spreads into its elements. A path that resolves to nothing
yields `null`, so a module written against one version of a service degrades to
"not found" instead of failing.

Where a service wraps a value in an object — `"artist": {"name": "M83"}` — the
common keys (`name`, `title`, `text`, `value`, `label`) are read automatically.

### `extract` fields

| Field | Meaning |
|---|---|
| `urlPath` | where the playable address is (required) |
| `fallbackUrlPath` | a second address, tried when the first is absent |
| `urlPrefix` | prefix for a relative address |
| `base64` | decode the address before using it |
| `format` | `progressive` (default), `hls`, `dash` |
| `headersPath` | a JSON object of headers the player should send |
| `codecPath`, `bitratePath`, `sampleRatePath` | optional, for the format badge |

### Fields

`kind` is `text`, `password`, `select` or `toggle`. A field with an empty default
counts as required: the module shows as needing setup until it is filled in.

## Limits

- 64 KB per document, 24 installed modules.
- `GET`, `POST`, `PUT` and `HEAD`.
- The loader and every module call share the app's HTTP client, so its proxy and
  DNS settings apply.

## A module that cannot play

A document may declare only a `search` capability. It will be listed and
installed, but it never serves audio — useful for checking a service's answers
before writing the stream half.
