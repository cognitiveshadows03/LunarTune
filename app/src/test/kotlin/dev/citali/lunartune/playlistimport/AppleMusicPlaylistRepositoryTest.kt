/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.playlistimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleMusicPlaylistRepositoryTest {
    @Test
    fun parsesPlaylistTracksFromAppleServerData() {
        val html =
            """
            <html>
              <head>
                <title>Late Night Mix - Playlist - Apple Music</title>
                <meta property="og:image" content="https://example.com/playlist.jpg">
                <script id="schema:music-playlist" type="application/ld+json">
                  {"@type":"MusicPlaylist","name":"Late Night Mix","track":[]}
                </script>
              </head>
              <body>
                <script id="serialized-server-data" type="application/json">
                  {"data":[{"id":"track-list - pl.test123","itemKind":"trackLockup","items":[
                    {"title":"Night Drive","duration":198500,"artistName":"Mira & The Waves","contentDescriptor":{"kind":"song","identifiers":{"storeAdamID":"123456"}},"subtitleLinks":[{"title":"Mira"},{"title":"The Waves"}],"artwork":{"dictionary":{"url":"https://example.com/{w}x{h}.{f}"}}},
                    {"title":"After Hours","duration":244000,"artistName":"Nova","contentDescriptor":{"kind":"song","identifiers":{"storeAdamID":"789012"}},"subtitleLinks":[{"title":"Nova"}]}
                  ]}]}
                </script>
              </body>
            </html>
            """.trimIndent()

        val playlist = parseAppleMusicPlaylistPage(html)

        assertEquals("Late Night Mix", playlist.title)
        assertEquals("https://example.com/playlist.jpg", playlist.artworkUrl)
        assertEquals(2, playlist.tracks.size)
        assertEquals("Night Drive", playlist.tracks[0].title)
        assertEquals(listOf("Mira", "The Waves"), playlist.tracks[0].artists)
        assertEquals(198, playlist.tracks[0].durationSeconds)
        assertEquals("123456", playlist.tracks[0].id)
        assertEquals("https://example.com/640x640.jpg", playlist.tracks[0].artworkUrl)
    }

    @Test
    fun schemaTrackMetadataIsUsedWhenPageModelIsUnavailable() {
        val html =
            """
            <html><head>
              <script id="schema:music-playlist" type="application/ld+json">
                {"@type":"MusicPlaylist","name":"Schema Mix","track":[{"@type":"MusicRecording","name":"One Song","url":"https://music.apple.com/us/song/one-song/456789","duration":"PT3M12S","audio":{"thumbnailUrl":"https://example.com/cover.jpg"}}]}
              </script>
            </head><body></body></html>
            """.trimIndent()

        val playlist = parseAppleMusicPlaylistPage(html)

        assertEquals("Schema Mix", playlist.title)
        assertEquals("One Song", playlist.tracks.single().title)
        assertEquals("456789", playlist.tracks.single().id)
        assertEquals(192, playlist.tracks.single().durationSeconds)
        assertEquals("https://example.com/cover.jpg", playlist.tracks.single().artworkUrl)
    }

    @Test
    fun acceptsAppleMusicPlaylistShareLinksAndDropsTrackingQuery() {
        val normalized =
            normalizeAppleMusicPlaylistUrl(
                "https://www.music.apple.com/us/playlist/late-night/pl.test123?i=456&l=en-US",
            )

        assertEquals("https://music.apple.com/us/playlist/late-night/pl.test123", normalized)
    }

    @Test
    fun rejectsNonAppleHostsAndNonPlaylistLinks() {
        val wrongHost = assertThrows(IllegalArgumentException::class.java) {
            normalizeAppleMusicPlaylistUrl("https://example.com/us/playlist/name/pl.test123")
        }
        val wrongPath = assertThrows(IllegalArgumentException::class.java) {
            normalizeAppleMusicPlaylistUrl("https://music.apple.com/us/song/title/123456")
        }

        assertTrue(wrongHost.message.orEmpty().contains("music.apple.com"))
        assertTrue(wrongPath.message.orEmpty().contains("playlist"))
    }
}
