# Addon modules

An addon is an ordinary HTTP server. It is not code that runs inside LunarTune:
nothing is downloaded and nothing is executed. The app makes requests, reads
JSON back, and plays the address the addon names. That is the whole contract, and
it is the same one the BitChord addon protocol uses, so a server written for that
works here unchanged.

## Adding one

Settings → Data sources → **Add an addon**, paste a base address:

```
https://example.com/music          ← this
https://example.com/music/manifest.json   ← or this
```

Both are accepted. A trailing `.json` filename is dropped, so the routes below
resolve the same way from either. The address is read first: the app asks for
`manifest.json` and shows what came back. Nothing is saved until an addon answers
and describes itself, so a mistyped address cannot sit in the list failing on
every track.

The address is the addon's identity. Adding the same one twice replaces the entry
rather than creating a second row that would be consulted twice.

## The three routes

All are `GET`, all answer `application/json`.

### `GET {base}/manifest.json`

```json
{
  "id": "my-catalogue",
  "name": "My Catalogue",
  "version": "1.0",
  "resources": ["search", "stream"],
  "settings": [
    {
      "key": "quality",
      "name": "Quality",
      "type": "select",
      "default": "LOSSLESS",
      "options": [
        { "value": "LOSSLESS", "name": "Lossless" },
        { "value": "HIGH", "name": "High" }
      ]
    }
  ],
  "allowDownloads": false,
  "checkValidLossless": false
}
```

`resources` is how the app knows what the addon can do. An addon without
`search` cannot be added: there would be no way to ask it for anything. `name` is
what the module row shows; it falls back to the address's host.

`settings` are the addon's own switches. They are shown on the module's page as
chips, and their values are sent as query parameters on every `search` and
`stream` call — that is how an addon that gates lossless behind a switch learns
which tier to serve.

### `GET {base}/search?q=…&quality=…`

```json
{
  "tracks": [
    {
      "id": "track-1",
      "title": "Song Title",
      "artist": "Artist",
      "album": "Album",
      "duration": 214.5,
      "artworkURL": "https://…/cover.jpg"
    }
  ]
}
```

`q` is the track's title and artist, space-separated. `duration` is in seconds and
may be a decimal or absent. `streamURL` may be included on a row — when it is,
the app plays it directly and skips the `stream` route, which saves a round trip
on the path that has to finish before audio starts.

### `GET {base}/stream/{id}?quality=…`

```json
{
  "url": "https://…/audio.flac",
  "format": "flac",
  "quality": "LOSSLESS",
  "codec": "FLAC",
  "container": "flac",
  "encrypted": false,
  "sampleRate": 44100,
  "bitDepth": 16,
  "bitrate": 1411200
}
```

`url` is what the player opens. It may be a progressive file, an HLS playlist
(`manifest: "hls"` or a `.m3u8` address) or DASH (`manifest: "dash"` or `.mpd`).

Anything with `encrypted` set to anything but `false` is refused. An addon that
serves its audio as an encrypted blob expects its own player to hold the key, and
this app will not pretend to be that player.

## When an addon has nothing

Two ways, both understood:

```
HTTP 404                                    ← no such route
HTTP 200 {"error": "not in this catalogue"}  ← an answer, but not a track
```

Either way the addon is not charged a failure — the next module in the order is
asked, and the addon's own words are what the diagnostics show.

## How an addon is used

An addon takes part in the same pipeline as the built-in modules:

* It has a row in **Installed**, a switch, and a position in the order.
* It is not asked at all unless the master switch is on and its own is on.
* It is asked in the order the arrow buttons set, after the built-ins.
* If it finds nothing, playback falls through to the next module and finally to
  YouTube Music, as before.

The matching is done on the app's side and is deliberately strict: candidates
whose duration disagrees with the track by more than the tolerance slider are
rejected outright, then the survivors are scored on title and artist. Addons are
catalogues of whole services, so the same song appears as a remaster, a live take
and a remix; taking the first row is how a lossless source ends up playing the
wrong thing.

## Hosting one

The protocol is small enough that a few dozen lines of Python or Node will do it.
Return the three documents above, point `url` at wherever the audio really lives,
and give the app the server's address. It can run on a machine on the same
network — a `http://192.168.x.x:8080` address is accepted.

There is no public directory of addons. The lists that circulated were GitHub
repositories whose files have since gone, so treat an addon address as something
you host or are given.

## What an addon is not

An addon cannot draw its own interface, cannot read the library, and cannot
change how the app behaves. It answers three questions about a track and nothing
else. That is why an addon needs no sandbox and no permission prompt — the only
thing it can do is name an address.
