/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.innertube

import io.ktor.client.call.body
import dev.citali.lunartune.constants.HideExplicitKey
import dev.citali.lunartune.utils.PreferenceStore
import moe.rukamori.archivetune.innertube.InnerTube
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.MusicResponsiveListItemRenderer
import moe.rukamori.archivetune.innertube.models.MusicTwoRowItemRenderer
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.WEB_REMIX
import moe.rukamori.archivetune.innertube.models.getContinuation
import moe.rukamori.archivetune.innertube.models.oddElements
import moe.rukamori.archivetune.innertube.models.response.BrowseResponse
import moe.rukamori.archivetune.innertube.pages.ChartsPage

/**
 * Standalone charts fetcher living in the app module.
 *
 * The `core` submodule pins upstream's InnerTube, whose charts parser only
 * understands the pre-2026 charts layout (a single ranked song carousel) and
 * silently drops every section the 2026 page actually contains: the playlist
 * carousels ("Video charts"), the artist carousels ("Top artists") and the
 * plain two-row album shelves. Because `core` is a submodule of another
 * repository, patching it here would never ship — so the charts browse is
 * issued directly through the (public) [InnerTube] client and parsed with
 * converters that cover all current shapes. Logic mirrors
 * `YouTube.getChartsPage` with the extra `isPlaylist` / `isArtist` /
 * two-column-row arms and no legacy browse `params` (the year-filter param
 * makes the endpoint respond with a page that carries no chart sections).
 *
 * Being a second client is not free: a fresh [InnerTube] starts with none of
 * the global configuration, so [syncGlobalClientConfig] mirrors it from
 * [YouTube] before every browse. Content filters are applied here rather than
 * by the callers so the Charts screen and Home discovery cannot drift apart.
 */
object ChartsApi {
    private val innerTube = InnerTube()

    /**
     * Mirrors the global InnerTube configuration onto this client.
     *
     * `App` pushes Content -> Country / Language into `YouTube.locale` (both at
     * startup and live from the settings screen) and proxy, DNS, PO token,
     * visitorData and the signed-in state into that same instance. A separate
     * [InnerTube] sees none of it, so without this the charts browse always went
     * out for the device locale and always bypassed the user's proxy.
     *
     * The proxy and DNS setters close and rebuild the OkHttp client, so they are
     * assigned only when they actually differ; copying them unconditionally would
     * tear down the connection pool on every charts fetch. The auth values are
     * assigned unconditionally so that signing out propagates.
     *
     * `InnerTube.proxySelector` is internal to `core`, so IP rotation cannot be
     * mirrored from the app module.
     */
    private fun syncGlobalClientConfig() {
        innerTube.locale = YouTube.locale
        innerTube.visitorData = YouTube.visitorData
        innerTube.dataSyncId = YouTube.dataSyncId
        innerTube.cookie = YouTube.cookie
        innerTube.poToken = YouTube.poToken
        innerTube.useLoginForBrowse = YouTube.useLoginForBrowse
        if (innerTube.proxy != YouTube.proxy) innerTube.proxy = YouTube.proxy
        if (innerTube.proxyUsername != YouTube.proxyUsername) {
            innerTube.proxyUsername = YouTube.proxyUsername
        }
        if (innerTube.proxyPassword != YouTube.proxyPassword) {
            innerTube.proxyPassword = YouTube.proxyPassword
        }
        if (innerTube.dns != YouTube.dns) innerTube.dns = YouTube.dns
    }

    /**
     * Content -> Hide explicit, applied to a parsed section.
     *
     * Read from the [PreferenceStore] snapshot instead of being threaded through
     * the callers: `PreferenceStore` is started in `App.onCreate` and awaited
     * before the UI comes up, and reading it here is what keeps the Charts screen
     * and `SearchDiscoveryRepository` filtering identically without either having
     * to grow a preference dependency.
     */
    private fun List<YTItem>.filterChartContent(hideExplicit: Boolean): List<YTItem> =
        if (hideExplicit) filterNot { it.explicit } else this

    suspend fun getChartsPage(continuation: String? = null): Result<ChartsPage> =
        runCatching {
            syncGlobalClientConfig()
            val hideExplicit = PreferenceStore.get(HideExplicitKey) == true

            val response =
                innerTube
                    .browse(
                        client = WEB_REMIX,
                        browseId = "FEmusic_charts",
                        continuation = continuation,
                    ).body<BrowseResponse>()

            val sections = mutableListOf<ChartsPage.ChartSection>()

            response.contents
                ?.singleColumnBrowseResultsRenderer
                ?.tabs
                ?.firstOrNull()
                ?.tabRenderer
                ?.content
                ?.sectionListRenderer
                ?.contents
                ?.forEach { content ->
                    content.musicCarouselShelfRenderer?.let { renderer ->
                        val title =
                            renderer.header
                                ?.musicCarouselShelfBasicHeaderRenderer
                                ?.title
                                ?.runs
                                ?.firstOrNull()
                                ?.text
                                ?: return@forEach

                        // Safe-call + let instead of null-comparison smart
                        // casts: these renderer props are cross-module public
                        // API, which Kotlin cannot smart-cast here.
                        val items =
                            renderer.contents
                                .mapNotNull { item ->
                                    item.musicResponsiveListItemRenderer
                                        ?.let { row -> convertToChartItem(row) }
                                        ?: item.musicTwoRowItemRenderer
                                            ?.let { row -> convertMusicTwoRowItem(row) }
                                }.filterChartContent(hideExplicit)

                        if (items.isNotEmpty()) {
                            sections.add(
                                ChartsPage.ChartSection(
                                    title = title,
                                    items = items,
                                    chartType = determineChartType(title),
                                ),
                            )
                        }
                    }

                    content.gridRenderer?.let { renderer ->
                        val title =
                            renderer.header
                                ?.gridHeaderRenderer
                                ?.title
                                ?.runs
                                ?.firstOrNull()
                                ?.text
                                ?: return@let

                        val items =
                            renderer.items
                                .mapNotNull { item ->
                                    item.musicTwoRowItemRenderer?.let { renderer ->
                                        convertMusicTwoRowItem(renderer)
                                    }
                                }.filterChartContent(hideExplicit)

                        if (items.isNotEmpty()) {
                            sections.add(
                                ChartsPage.ChartSection(
                                    title = title,
                                    items = items,
                                    chartType = ChartsPage.ChartType.NEW_RELEASES,
                                ),
                            )
                        }
                    }
                }

            ChartsPage(
                sections = sections,
                continuation =
                    response.continuationContents
                        ?.sectionListContinuation
                        ?.continuations
                        ?.getContinuation(),
            )
        }

    private fun determineChartType(title: String): ChartsPage.ChartType =
        when {
            title.contains("Trending", ignoreCase = true) -> ChartsPage.ChartType.TRENDING
            title.contains("Top", ignoreCase = true) -> ChartsPage.ChartType.TOP
            else -> ChartsPage.ChartType.GENRE
        }

    private fun convertToChartItem(renderer: MusicResponsiveListItemRenderer): YTItem? =
        try {
            val playlistData = renderer.playlistItemData
            when {
                renderer.flexColumns.size >= 3 && playlistData != null -> {
                    // Legacy ranked "Top songs" row: name + artists + position.
                    val firstColumn =
                        renderer.flexColumns
                            .getOrNull(0)
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text ?: return null
                    val secondColumn =
                        renderer.flexColumns
                            .getOrNull(1)
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text ?: return null
                    val title =
                        firstColumn.runs
                            ?.firstOrNull()
                            ?.text
                            ?.takeIf { it.isNotBlank() } ?: return null
                    val artists =
                        secondColumn.runs?.mapNotNull { run ->
                            run.text
                                .takeIf { it.isNotBlank() }
                                ?.let { name ->
                                    Artist(
                                        name = name,
                                        id = run.navigationEndpoint?.browseEndpoint?.browseId,
                                    )
                                }
                        } ?: emptyList()
                    val thirdColumn =
                        renderer.flexColumns
                            .getOrNull(2)
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text
                    val thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getBestThumbnail() ?: return null
                    SongItem(
                        id = playlistData.videoId,
                        title = title,
                        artists = artists,
                        thumbnail = thumbnail.normalizedUrl,
                        thumbnailWidth = thumbnail.width,
                        thumbnailHeight = thumbnail.height,
                        explicit =
                            renderer.badges?.any {
                                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                            } == true,
                        chartPosition =
                            thirdColumn
                                ?.runs
                                ?.firstOrNull()
                                ?.text
                                ?.toIntOrNull(),
                        chartChange = thirdColumn?.runs?.getOrNull(1)?.text,
                    )
                }

                renderer.flexColumns.size == 2 -> {
                    // 2026 "Top artists" rows: name + subscribers, navigating
                    // to the artist page. Anything else with this shape keeps
                    // the title as-is and still links through the row's browse
                    // endpoint when one exists.
                    val titleColumn =
                        renderer.flexColumns
                            .getOrNull(0)
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text
                    val name =
                        titleColumn
                            ?.runs
                            ?.firstOrNull()
                            ?.text
                            ?.takeIf { it.isNotBlank() } ?: return null
                    val browseId =
                        titleColumn
                            .runs
                            ?.firstOrNull()
                            ?.navigationEndpoint
                            ?.browseEndpoint
                            ?.browseId
                            ?: renderer.navigationEndpoint?.browseEndpoint?.browseId
                            ?: return null
                    val subtitleColumn =
                        renderer.flexColumns
                            .getOrNull(1)
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text
                    ArtistItem(
                        id = browseId,
                        title = name,
                        thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getBestThumbnail()?.normalizedUrl,
                        channelId = browseId,
                        playEndpoint = null,
                        shuffleEndpoint = null,
                        radioEndpoint = null,
                        subscriberCountText =
                            subtitleColumn
                                ?.runs
                                ?.firstOrNull()
                                ?.text,
                    )
                }

                else -> null
            }
        } catch (e: Exception) {
            null
        }

    private fun convertMusicTwoRowItem(renderer: MusicTwoRowItemRenderer): YTItem? =
        try {
            when {
                renderer.isSong -> {
                    val subtitle = renderer.subtitle?.runs ?: return null
                    val thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getBestThumbnail() ?: return null
                    SongItem(
                        id = renderer.navigationEndpoint.watchEndpoint?.videoId ?: return null,
                        title =
                            renderer.title.runs
                                ?.firstOrNull()
                                ?.text ?: return null,
                        artists =
                            subtitle.mapNotNull {
                                it.navigationEndpoint?.browseEndpoint?.browseId?.let { id ->
                                    Artist(name = it.text, id = id)
                                }
                            },
                        thumbnail = thumbnail.normalizedUrl,
                        thumbnailWidth = thumbnail.width,
                        thumbnailHeight = thumbnail.height,
                        explicit =
                            renderer.subtitleBadges?.any {
                                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                            } == true,
                    )
                }

                renderer.isAlbum -> {
                    val thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getBestThumbnail() ?: return null
                    AlbumItem(
                        browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                        playlistId =
                            renderer.thumbnailOverlay
                                ?.musicItemThumbnailOverlayRenderer
                                ?.content
                                ?.musicPlayButtonRenderer
                                ?.playNavigationEndpoint
                                ?.watchPlaylistEndpoint
                                ?.playlistId ?: return null,
                        title =
                            renderer.title.runs
                                ?.firstOrNull()
                                ?.text ?: return null,
                        artists =
                            renderer.subtitle?.runs?.oddElements()?.drop(1)?.mapNotNull {
                                it.navigationEndpoint?.browseEndpoint?.browseId?.let { id ->
                                    Artist(name = it.text, id = id)
                                }
                            },
                        year =
                            renderer.subtitle
                                ?.runs
                                ?.lastOrNull()
                                ?.text
                                ?.toIntOrNull(),
                        thumbnail = thumbnail.normalizedUrl,
                        thumbnailWidth = thumbnail.width,
                        thumbnailHeight = thumbnail.height,
                        explicit =
                            renderer.subtitleBadges?.any {
                                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                            } == true,
                    )
                }

                // Charts surface playlists (e.g. "Video charts") and artists
                // as two-row items too; the chart view only needs navigation
                // + title, so optional fields degrade to null instead of
                // dropping the whole row.
                renderer.isPlaylist -> {
                    val browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null
                    val overlayEndpoint =
                        renderer.thumbnailOverlay
                            ?.musicItemThumbnailOverlayRenderer
                            ?.content
                            ?.musicPlayButtonRenderer
                            ?.playNavigationEndpoint
                    PlaylistItem(
                        id = browseId.removePrefix("VL"),
                        title =
                            renderer.title.runs
                                ?.firstOrNull()
                                ?.text ?: return null,
                        author =
                            renderer.subtitle?.runs?.mapNotNull { run ->
                                run.navigationEndpoint?.browseEndpoint?.browseId?.let { id ->
                                    Artist(name = run.text, id = id)
                                }
                            }?.lastOrNull(),
                        songCountText =
                            renderer.subtitle
                                ?.runs
                                ?.lastOrNull()
                                ?.text,
                        thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getBestThumbnail()?.normalizedUrl,
                        playEndpoint = overlayEndpoint?.watchPlaylistEndpoint,
                        shuffleEndpoint = null,
                        radioEndpoint = null,
                    )
                }

                renderer.isArtist -> {
                    val browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null
                    ArtistItem(
                        id = browseId,
                        title =
                            renderer.title.runs
                                ?.firstOrNull()
                                ?.text ?: return null,
                        thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getBestThumbnail()?.normalizedUrl,
                        channelId = browseId.takeIf { it.startsWith("UC") },
                        playEndpoint = null,
                        shuffleEndpoint = null,
                        radioEndpoint = null,
                        subscriberCountText =
                            renderer.subtitle
                                ?.runs
                                ?.lastOrNull()
                                ?.text,
                    )
                }

                else -> null
            }
        } catch (e: Exception) {
            null
        }
}
