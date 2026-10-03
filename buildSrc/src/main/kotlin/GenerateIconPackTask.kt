/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.apache.batik.transcoder.SVGAbstractTranscoder
import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.batik.transcoder.image.PNGTranscoder
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs

abstract class GenerateIconPackTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val metadataFile: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val svgDirectory: DirectoryProperty

    @get:Input
    abstract val applicationId: Property<String>

    @get:Input
    abstract val targetActivityClassName: Property<String>

    @get:OutputDirectory
    abstract val resourceOutputDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val assetOutputDirectory: DirectoryProperty

    @get:OutputFile
    abstract val manifestOutputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val metadata = resolveEntries(parseMetadata())
        val resourcesDirectory = resourceOutputDirectory.get().asFile
        val assetsDirectory = assetOutputDirectory.get().asFile
        val manifestFile = manifestOutputFile.get().asFile

        resourcesDirectory.deleteRecursively()
        assetsDirectory.deleteRecursively()
        resourcesDirectory.mkdirs()
        assetsDirectory.mkdirs()
        manifestFile.parentFile.mkdirs()

        val seenIds = mutableSetOf<String>()
        val seenHashes = mutableSetOf<String>()
        val catalog =
            metadata.mapIndexed { index, rawEntry ->
                val entry =
                    rawEntry as? Map<*, *>
                        ?: throw GradleException("IconPack metadata entry $index must be an object.")
                val id = entry.requiredString("Id", index)
                val name = entry.requiredString("Name", index)
                val author = entry.requiredString("Author", index)
                val source = entry.requiredString("Source", index)
                val link = entry.optionalString("Link")
                val githubAuthorUrl = entry.optionalString("GitHubAuthorUrl")
                val backgroundMode = entry.optionalString("BackgroundMode")
                val configuredBackgroundColor = entry.optionalString("BackgroundColor")
                val adaptiveInset =
                    entry.optionalString("AdaptiveInset").ifEmpty { DefaultAdaptiveInset }.let { raw ->
                        val dp = raw.removeSuffix("dp").trim().toDoubleOrNull()
                        if (dp == null || dp < 0.0 || dp > 36.0) {
                            throw GradleException(
                                "IconPack AdaptiveInset \"$raw\" for Id \"$id\" must be a number between 0 and 36 (dp).",
                            )
                        }
                        "${dp}dp"
                    }

                if (id == DefaultIconId) {
                    throw GradleException("IconPack Id \"$DefaultIconId\" is reserved.")
                }
                if (!seenIds.add(id)) {
                    throw GradleException("IconPack metadata contains duplicate Id \"$id\".")
                }

                val hash = id.sha256Prefix()
                if (!seenHashes.add(hash)) {
                    throw GradleException("IconPack generated identifier collision for Id \"$id\".")
                }

                val sourceFile = resolveSource(source)
                val analysis = analyzeSvg(sourceFile)
                val drawableName = "icon_pack_$hash"
                val targetFile = File(resourcesDirectory, "drawable-nodpi/$drawableName.png")
                targetFile.parentFile.mkdirs()
                rasterizeSvg(sourceFile, targetFile)
                val rasterEdge = targetFile.trimAndAnalyzeEdges()
                val hasIntegratedBackground =
                    when (backgroundMode.lowercase()) {
                        "" -> {
                            rasterEdge.opaqueEdges || analysis.hasIntegratedBackground
                        }

                        IntegratedBackgroundMode -> {
                            true
                        }

                        TransparentBackgroundMode -> {
                            false
                        }

                        else -> {
                            throw GradleException(
                                "IconPack BackgroundMode \"$backgroundMode\" for Id \"$id\" must be " +
                                    "\"$IntegratedBackgroundMode\" or \"$TransparentBackgroundMode\".",
                            )
                        }
                    }
                val backgroundColor =
                    if (configuredBackgroundColor.isEmpty()) {
                        if (hasIntegratedBackground) {
                            rasterEdge.edgeColor
                                ?: targetFile.readOpaqueCornerColor()
                                ?: analysis.recommendedBackgroundColor
                        } else {
                            analysis.recommendedBackgroundColor
                        }
                    } else {
                        configuredBackgroundColor.toOpaqueColor()
                            ?: throw GradleException(
                                "IconPack BackgroundColor \"$configuredBackgroundColor\" for Id \"$id\" " +
                                    "must be a literal hex color.",
                            )
                    }

                mapOf(
                    "id" to id,
                    "name" to name,
                    "author" to author,
                    "link" to link,
                    "githubAuthorUrl" to githubAuthorUrl,
                    "source" to source,
                    "drawableResourceName" to drawableName,
                    "adaptiveIconResourceName" to drawableName,
                    "roundAdaptiveIconResourceName" to "${drawableName}_round",
                    "backgroundColor" to backgroundColor,
                    "integratedBackground" to hasIntegratedBackground.toString(),
                    "adaptiveInset" to adaptiveInset,
                    "aliasClassName" to "${applicationId.get()}.launcher.IconAlias$hash",
                )
            }

        writeAdaptiveIconResources(resourcesDirectory, catalog)
        File(assetsDirectory, CatalogAssetPath).apply {
            parentFile.mkdirs()
            val runtimeCatalog =
                catalog.map { entry ->
                    entry -
                        setOf(
                            "adaptiveIconResourceName",
                            "roundAdaptiveIconResourceName",
                            "backgroundColor",
                            "integratedBackground",
                            "adaptiveInset",
                        )
                }
            writeText(JsonOutput.prettyPrint(JsonOutput.toJson(runtimeCatalog)) + System.lineSeparator())
        }
        manifestFile.writeText(buildManifest(catalog))
    }

    /**
     * LunarTune: the icon list is driven by the SVGs that actually exist in IconPack/svg.
     * Metadata entries whose Source file is missing are skipped, and any SVG without a
     * metadata entry is added automatically (name from the file name, stable Id from its hash),
     * so dropping a new SVG into the IconPack repo is enough to ship it.
     */
    private fun resolveEntries(rawMetadata: List<*>): List<Map<String, Any?>> {
        val root = svgDirectory.get().asFile.canonicalFile
        val available =
            root
                .listFiles { file -> file.isFile && file.extension.equals("svg", ignoreCase = true) }
                .orEmpty()
                .filter { file ->
                    (file.length() <= MaxSvgSizeBytes).also { accepted ->
                        if (!accepted) logger.warn("IconPack: skipping ${file.name} (larger than 700KB).")
                    }
                }
                .sortedBy { file -> file.name.lowercase() }
        val availableNames = available.map { file -> file.name }.toSet()

        val listed =
            rawMetadata.mapIndexedNotNull { index, rawEntry ->
                val entry =
                    rawEntry as? Map<*, *>
                        ?: throw GradleException("IconPack metadata entry $index must be an object.")
                val source = (entry["Source"] as? String)?.trim().orEmpty()
                if (source !in availableNames) {
                    logger.info("IconPack: skipping metadata entry \"$source\" (SVG not present).")
                    null
                } else {
                    entry.entries.associate { (key, value) -> key.toString() to value }
                }
            }
        val listedSources = listed.map { entry -> entry["Source"] as String }.toSet()

        val discovered =
            available
                .filterNot { file -> file.name in listedSources }
                .map { file ->
                    val name = file.nameWithoutExtension.replace('_', ' ').replace('-', ' ').trim()
                    mapOf<String, Any?>(
                        "Id" to "auto_" + file.name.lowercase().sha256Prefix(),
                        "Name" to name,
                        "Author" to DefaultIconAuthor,
                        "GitHubAuthorUrl" to DefaultIconAuthorUrl,
                        "Source" to file.name,
                    )
                }

        return listed + discovered
    }

    private fun parseMetadata(): List<*> {
        val metadataText = metadataFile.get().asFile.readText()
        val parsed =
            try {
                JsonSlurper().parseText(metadataText)
            } catch (error: Exception) {
                val repairedMetadata = metadataText.repairMissingTopLevelObjectCommas()
                if (repairedMetadata == metadataText) {
                    throw GradleException("IconPack metadata.json is invalid.", error)
                }
                try {
                    logger.warn(
                        "IconPack metadata.json is missing commas between entries; parsing a repaired copy. " +
                            "Please correct the source metadata.",
                    )
                    JsonSlurper().parseText(repairedMetadata)
                } catch (repairError: Exception) {
                    throw GradleException(
                        "IconPack metadata.json is invalid even after repairing missing entry separators.",
                        repairError,
                    )
                }
            }
        return parsed as? List<*>
            ?: throw GradleException("IconPack metadata.json must contain a JSON array.")
    }

    /** Repair only missing commas between adjacent objects in the root metadata array. */
    private fun String.repairMissingTopLevelObjectCommas(): String {
        val repaired = StringBuilder(length + 2)
        var arrayDepth = 0
        var objectDepth = 0
        var inString = false
        var escaped = false
        var needsComma = false

        for (character in this) {
            if (inString) {
                repaired.append(character)
                if (escaped) {
                    escaped = false
                } else {
                    when (character) {
                        '\\' -> escaped = true
                        '"' -> inString = false
                    }
                }
                continue
            }

            if (needsComma) {
                if (character.isWhitespace()) {
                    repaired.append(character)
                    continue
                }
                if (character == '{') repaired.append(',')
                needsComma = false
            }

            repaired.append(character)
            when (character) {
                '"' -> inString = true
                '[' -> arrayDepth++
                ']' -> arrayDepth--
                '{' -> objectDepth++
                '}' -> {
                    objectDepth--
                    if (arrayDepth == 1 && objectDepth == 0) needsComma = true
                }
            }
        }

        return repaired.toString()
    }

    private fun resolveSource(source: String): File {
        if (!source.endsWith(".svg", ignoreCase = true) ||
            source.contains('/') ||
            source.contains('\\')
        ) {
            throw GradleException("IconPack Source \"$source\" must be an SVG basename.")
        }

        val root = svgDirectory.get().asFile.canonicalFile
        val sourceFile = File(root, source).canonicalFile
        if (sourceFile.parentFile != root || !sourceFile.isFile) {
            throw GradleException("IconPack Source \"$source\" is missing from ${root.path}.")
        }
        return sourceFile
    }

    private fun rasterizeSvg(
        sourceFile: File,
        targetFile: File,
    ) {
        val output = ByteArrayOutputStream()
        try {
            val transcoder =
                PNGTranscoder().apply {
                    addTranscodingHint(SVGAbstractTranscoder.KEY_WIDTH, IconRasterSize.toFloat())
                    addTranscodingHint(SVGAbstractTranscoder.KEY_HEIGHT, IconRasterSize.toFloat())
                    addTranscodingHint(SVGAbstractTranscoder.KEY_EXECUTE_ONLOAD, false)
                    addTranscodingHint(SVGAbstractTranscoder.KEY_ALLOW_EXTERNAL_RESOURCES, false)
                }
            sourceFile.inputStream().buffered().use { input ->
                transcoder.transcode(
                    TranscoderInput(input),
                    TranscoderOutput(output),
                )
            }
        } catch (error: Exception) {
            throw GradleException(
                "Unable to rasterize IconPack Source \"${sourceFile.name}\".",
                error,
            )
        }
        val png = output.toByteArray()
        if (png.size < PngSignature.size ||
            PngSignature.indices.any { index -> png[index] != PngSignature[index] }
        ) {
            throw GradleException(
                "IconPack Source \"${sourceFile.name}\" produced an invalid PNG.",
            )
        }
        targetFile.writeBytes(png)
    }

    private fun analyzeSvg(sourceFile: File): SvgAnalysis {
        val document = parseSvg(sourceFile)
        val root = document.documentElement
        if (root.localName != "svg") {
            throw GradleException("IconPack Source \"${sourceFile.name}\" must have an <svg> root.")
        }

        val viewBox = parseViewBox(root, sourceFile)
        val paths = document.getElementsByTagNameNS(SvgNamespace, "path")
        val rectangles = document.getElementsByTagNameNS(SvgNamespace, "rect")
        if (paths.length == 0 && rectangles.length == 0) {
            throw GradleException(
                "IconPack Source \"${sourceFile.name}\" must contain vector artwork.",
            )
        }

        val colors = mutableListOf<String>()
        var hasFullCanvasShape = false
        for (index in 0 until paths.length) {
            val path = paths.item(index) as? Element ?: continue
            if (!path.isVisible()) continue
            path.resolveFillColor()?.let(colors::add)
            if (path.pathCoversViewBox(viewBox)) {
                hasFullCanvasShape = true
            }
        }
        for (index in 0 until rectangles.length) {
            val rectangle = rectangles.item(index) as? Element ?: continue
            if (!rectangle.isVisible()) continue
            rectangle.resolveFillColor()?.let(colors::add)
            if (rectangle.rectangleCoversViewBox(viewBox)) {
                hasFullCanvasShape = true
            }
        }

        val hasIntegratedBackground =
            hasFullCanvasShape ||
                (
                    paths.length + rectangles.length >= ComplexArtworkPathThreshold &&
                        colors.toSet().size >= ComplexArtworkColorThreshold
                )
        val dominantColor =
            colors
                .groupingBy { color -> color.uppercase() }
                .eachCount()
                .maxByOrNull { entry -> entry.value }
                ?.key

        return SvgAnalysis(
            hasIntegratedBackground = hasIntegratedBackground,
            recommendedBackgroundColor = dominantColor.contrastingBackground(),
        )
    }

    private fun parseSvg(sourceFile: File): Document {
        val factory =
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                setAttribute(AccessExternalDtdProperty, "")
                setAttribute(AccessExternalSchemaProperty, "")
            }
        return try {
            factory.newDocumentBuilder().parse(sourceFile)
        } catch (error: Exception) {
            throw GradleException("IconPack Source \"${sourceFile.name}\" is not valid SVG.", error)
        }
    }

    private fun parseViewBox(
        root: Element,
        sourceFile: File,
    ): ViewBox {
        val values =
            root
                .getAttribute("viewBox")
                .trim()
                .split(WhitespaceOrComma)
                .filter(String::isNotEmpty)
                .mapNotNull(String::toDoubleOrNull)
        if (values.size != ViewBoxValueCount || values[2] <= 0.0 || values[3] <= 0.0) {
            throw GradleException(
                "IconPack Source \"${sourceFile.name}\" requires a valid four-value viewBox.",
            )
        }
        return ViewBox(
            minX = values[0],
            minY = values[1],
            width = values[2],
            height = values[3],
        )
    }

    private fun Element.isVisible(): Boolean {
        var current: Node? = this
        while (current is Element) {
            val display = current.getAttribute("display").ifEmpty { current.styleValue("display") }
            if (display.equals("none", ignoreCase = true)) return false
            val opacity = current.getAttribute("opacity").ifEmpty { current.styleValue("opacity") }
            if (opacity.toDoubleOrNull() == 0.0) return false
            current = current.parentNode
        }
        return true
    }

    private fun Element.resolveFillColor(): String? {
        var current: Node? = this
        while (current is Element) {
            val fill = current.getAttribute("fill").ifEmpty { current.styleValue("fill") }
            if (fill.isNotEmpty()) {
                if (fill.equals("none", ignoreCase = true)) return null
                return fill.toOpaqueColor()
            }
            current = current.parentNode
        }
        return DefaultPathFillColor
    }

    private fun Element.styleValue(name: String): String =
        getAttribute("style")
            .split(';')
            .asSequence()
            .map { declaration -> declaration.split(':', limit = 2) }
            .firstOrNull { parts -> parts.size == 2 && parts[0].trim() == name }
            ?.get(1)
            ?.trim()
            .orEmpty()

    private fun Element.pathCoversViewBox(viewBox: ViewBox): Boolean {
        val pathData = getAttribute("d")
        val coordinates =
            NumberPattern
                .findAll(pathData)
                .take(FullCanvasCoordinateCount)
                .mapNotNull { match -> match.value.toDoubleOrNull() }
                .toList()
        if (coordinates.size != FullCanvasCoordinateCount) return false
        val tolerance = maxOf(viewBox.width, viewBox.height) * FullCanvasToleranceRatio
        return coordinates[0].approximatelyEquals(viewBox.minX, tolerance) &&
            coordinates[1].approximatelyEquals(viewBox.minY, tolerance) &&
            coordinates[2].approximatelyEquals(viewBox.maxX, tolerance) &&
            coordinates[3].approximatelyEquals(viewBox.minY, tolerance) &&
            coordinates[4].approximatelyEquals(viewBox.maxX, tolerance) &&
            coordinates[5].approximatelyEquals(viewBox.maxY, tolerance) &&
            coordinates[6].approximatelyEquals(viewBox.minX, tolerance) &&
            coordinates[7].approximatelyEquals(viewBox.maxY, tolerance)
    }

    private fun Element.rectangleCoversViewBox(viewBox: ViewBox): Boolean {
        val x = getAttribute("x").ifEmpty { "0" }.toDoubleOrNull() ?: return false
        val y = getAttribute("y").ifEmpty { "0" }.toDoubleOrNull() ?: return false
        val width = getAttribute("width").toDoubleOrNull() ?: return false
        val height = getAttribute("height").toDoubleOrNull() ?: return false
        val tolerance = maxOf(viewBox.width, viewBox.height) * FullCanvasToleranceRatio
        return x <= viewBox.minX + tolerance &&
            y <= viewBox.minY + tolerance &&
            x + width >= viewBox.maxX - tolerance &&
            y + height >= viewBox.maxY - tolerance
    }

    private fun writeAdaptiveIconResources(
        resourcesDirectory: File,
        catalog: List<Map<String, String>>,
    ) {
        val drawableDirectory = File(resourcesDirectory, "drawable")
        if (!drawableDirectory.isDirectory && !drawableDirectory.mkdirs()) {
            throw GradleException(
                "Unable to create adaptive icon drawable directory ${drawableDirectory.path}.",
            )
        }

        catalog.forEach { entry ->
            val resourceName = entry.getValue("adaptiveIconResourceName")
            val roundResourceName = entry.getValue("roundAdaptiveIconResourceName")
            val drawableName = entry.getValue("drawableResourceName")
            val backgroundName = "${drawableName}_background"
            val foregroundName = "${drawableName}_foreground"
            val backgroundColor = entry.getValue("backgroundColor")

            val integrated = entry.getValue("integratedBackground").toBoolean()
            if (integrated) {
                // Full-bleed adaptive icon: the artwork fills the whole 108dp background layer so the
                // launcher mask (circle, squircle, teardrop...) crops the art itself, with no plate or
                // ring around it. AdaptiveInset (metadata) shrinks the art if its edges get cropped too much.
                File(drawableDirectory, "$backgroundName.xml").writeText(
                    """
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="$AndroidNamespace">
    <item>
        <shape android:shape="rectangle">
            <solid android:color="$backgroundColor" />
        </shape>
    </item>
    <item
        android:bottom="${entry.getValue("adaptiveInset")}"
        android:drawable="@drawable/$drawableName"
        android:left="${entry.getValue("adaptiveInset")}"
        android:right="${entry.getValue("adaptiveInset")}"
        android:top="${entry.getValue("adaptiveInset")}" />
</layer-list>
                    """.trimIndent() + System.lineSeparator(),
                )
                File(drawableDirectory, "$foregroundName.xml").writeText(
                    """
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="$AndroidNamespace"
    android:shape="rectangle">
    <solid android:color="@android:color/transparent" />
</shape>
                    """.trimIndent() + System.lineSeparator(),
                )
            } else {
                // Transparent artwork: keep it inside the safe zone on a solid background.
                File(drawableDirectory, "$backgroundName.xml").writeText(
                    """
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="$AndroidNamespace"
    android:shape="rectangle">
    <solid android:color="$backgroundColor" />
</shape>
                    """.trimIndent() + System.lineSeparator(),
                )
                File(drawableDirectory, "$foregroundName.xml").writeText(
                    """
<?xml version="1.0" encoding="utf-8"?>
<inset xmlns:android="$AndroidNamespace"
    android:drawable="@drawable/$drawableName"
    android:insetBottom="$AdaptiveIconForegroundInset"
    android:insetLeft="$AdaptiveIconForegroundInset"
    android:insetRight="$AdaptiveIconForegroundInset"
    android:insetTop="$AdaptiveIconForegroundInset" />
                    """.trimIndent() + System.lineSeparator(),
                )
            }

            writeAdaptiveIconWrapper(
                targetFile = File(resourcesDirectory, "mipmap-anydpi-v26/$resourceName.xml"),
                backgroundName = backgroundName,
                foregroundName = foregroundName,
            )
            writeAdaptiveIconWrapper(
                targetFile = File(resourcesDirectory, "mipmap-anydpi-v26/$roundResourceName.xml"),
                backgroundName = backgroundName,
                foregroundName = foregroundName,
            )
        }
    }

    private fun writeAdaptiveIconWrapper(
        targetFile: File,
        backgroundName: String,
        foregroundName: String,
    ) {
        targetFile.parentFile.mkdirs()
        targetFile.writeText(
            """
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="$AndroidNamespace">
    <background android:drawable="@drawable/$backgroundName" />
    <foreground android:drawable="@drawable/$foregroundName" />
</adaptive-icon>
            """.trimIndent() + System.lineSeparator(),
        )
    }

    private fun buildManifest(catalog: List<Map<String, String>>): String {
        val packageName = applicationId.get()
        val aliases =
            catalog.joinToString(separator = System.lineSeparator()) { entry ->
                val aliasClassName = entry.getValue("aliasClassName")
                val adaptiveIconResourceName = entry.getValue("adaptiveIconResourceName")
                val roundAdaptiveIconResourceName = entry.getValue("roundAdaptiveIconResourceName")
                """
        <activity-alias
            android:name="$aliasClassName"
            android:enabled="false"
            android:exported="true"
            android:icon="@mipmap/$adaptiveIconResourceName"
            android:label="@string/app_name"
            android:roundIcon="@mipmap/$roundAdaptiveIconResourceName"
            android:targetActivity="${targetActivityClassName.get()}">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
                <category android:name="android.intent.category.APP_MUSIC" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity-alias>
                """.trimEnd()
            }

        return """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="$AndroidNamespace">
    <application>
        <activity-alias
            android:name="$packageName.launcher.DefaultIconAlias"
            android:enabled="true"
            android:exported="true"
            android:icon="@mipmap/ic_launcher"
            android:label="@string/app_name"
            android:roundIcon="@mipmap/ic_launcher_round"
            android:targetActivity="${targetActivityClassName.get()}">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
                <category android:name="android.intent.category.APP_MUSIC" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity-alias>
${aliases.prependIndent("        ")}
    </application>
</manifest>
            """.trimIndent() + System.lineSeparator()
    }

    private fun Map<*, *>.requiredString(
        key: String,
        index: Int,
    ): String =
        this[key]
            ?.let { value ->
                when (value) {
                    is String,
                    is Number,
                    -> value.toString()

                    else -> null
                }
            }?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: throw GradleException("IconPack metadata entry $index requires a non-empty $key.")

    private fun Map<*, *>.optionalString(key: String): String = (this[key] as? String)?.trim().orEmpty()

    private fun String.sha256Prefix(): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private data class RasterEdge(
        val opaqueEdges: Boolean,
        val edgeColor: String?,
    )

    /**
     * Trims transparent padding around the artwork (re-centring it on a square canvas), then samples
     * the outer edge. If most edge pixels are opaque the artwork carries its own background, so it
     * can be used full-bleed in an adaptive icon, and the average edge colour fills any gaps.
     */
    private fun File.trimAndAnalyzeEdges(): RasterEdge {
        val source = ImageIO.read(this) ?: return RasterEdge(false, null)
        var minX = source.width
        var minY = source.height
        var maxX = -1
        var maxY = -1
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                if ((source.getRGB(x, y) ushr AlphaChannelShift) > EdgeAlphaThreshold) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < 0) return RasterEdge(false, null)

        val contentWidth = maxX - minX + 1
        val contentHeight = maxY - minY + 1
        val side = maxOf(contentWidth, contentHeight)
        val trimmed =
            if (side < source.width || side < source.height) {
                val square = java.awt.image.BufferedImage(side, side, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                val graphics = square.createGraphics()
                graphics.drawImage(
                    source.getSubimage(minX, minY, contentWidth, contentHeight),
                    (side - contentWidth) / 2,
                    (side - contentHeight) / 2,
                    null,
                )
                graphics.dispose()
                val scaled = java.awt.image.BufferedImage(IconRasterSize, IconRasterSize, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                val scaledGraphics = scaled.createGraphics()
                scaledGraphics.setRenderingHint(
                    java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC,
                )
                scaledGraphics.drawImage(square, 0, 0, IconRasterSize, IconRasterSize, null)
                scaledGraphics.dispose()
                ImageIO.write(scaled, "png", this)
                scaled
            } else {
                source
            }

        val w = trimmed.width
        val h = trimmed.height
        val edgePixels = ArrayList<Int>((w + h) * 2)
        for (x in 0 until w) {
            edgePixels += trimmed.getRGB(x, EdgeSampleInset)
            edgePixels += trimmed.getRGB(x, h - 1 - EdgeSampleInset)
        }
        for (y in 0 until h) {
            edgePixels += trimmed.getRGB(EdgeSampleInset, y)
            edgePixels += trimmed.getRGB(w - 1 - EdgeSampleInset, y)
        }
        val opaque = edgePixels.filter { color -> (color ushr AlphaChannelShift) == OpaqueAlpha }
        val opaqueEdges = opaque.size >= edgePixels.size * OpaqueEdgeRatio
        if (opaque.isEmpty()) return RasterEdge(false, null)
        val red = opaque.sumOf { color -> color ushr RedChannelShift and ColorChannelMask } / opaque.size
        val green = opaque.sumOf { color -> color ushr GreenChannelShift and ColorChannelMask } / opaque.size
        val blue = opaque.sumOf { color -> color and ColorChannelMask } / opaque.size
        return RasterEdge(opaqueEdges, "#FF%02X%02X%02X".format(red, green, blue))
    }

    private fun File.readOpaqueCornerColor(): String? {
        val image =
            try {
                ImageIO.read(this)
            } catch (error: Exception) {
                throw GradleException("Unable to inspect generated icon raster \"$name\".", error)
            } ?: throw GradleException("Generated icon raster \"$name\" is not a readable image.")
        val corners =
            intArrayOf(
                image.getRGB(0, 0),
                image.getRGB(image.width - 1, 0),
                image.getRGB(0, image.height - 1),
                image.getRGB(image.width - 1, image.height - 1),
            )
        if (corners.any { color -> color ushr AlphaChannelShift != OpaqueAlpha }) {
            return null
        }
        val red = corners.sumOf { color -> color ushr RedChannelShift and ColorChannelMask } / corners.size
        val green = corners.sumOf { color -> color ushr GreenChannelShift and ColorChannelMask } / corners.size
        val blue = corners.sumOf { color -> color and ColorChannelMask } / corners.size
        return "#FF%02X%02X%02X".format(red, green, blue)
    }

    private fun String.toOpaqueColor(): String? {
        val value = trim()
        val argb =
            when {
                value.matches(ShortRgbColor) -> "FF${value.substring(1).map { "$it$it" }.joinToString("")}"
                value.matches(ShortArgbColor) -> value.substring(1).map { "$it$it" }.joinToString("")
                value.matches(RgbColor) -> "FF${value.substring(1)}"
                value.matches(ArgbColor) -> value.substring(1)
                else -> return null
            }
        if (argb.substring(0, 2).equals("00", ignoreCase = true)) {
            return null
        }
        return "#FF${argb.substring(2).uppercase()}"
    }

    private fun String?.contrastingBackground(): String {
        val color = this ?: return DefaultTransparentIconBackground
        val red = color.substring(3, 5).toInt(16) / ColorChannelMax
        val green = color.substring(5, 7).toInt(16) / ColorChannelMax
        val blue = color.substring(7, 9).toInt(16) / ColorChannelMax
        val luminance =
            RedLuminanceWeight * red +
                GreenLuminanceWeight * green +
                BlueLuminanceWeight * blue
        return if (luminance < DarkColorLuminanceThreshold) {
            LightContrastingBackground
        } else {
            DarkContrastingBackground
        }
    }

    private fun Double.approximatelyEquals(
        other: Double,
        tolerance: Double,
    ): Boolean = abs(this - other) <= tolerance

    private data class SvgAnalysis(
        val hasIntegratedBackground: Boolean,
        val recommendedBackgroundColor: String,
    )

    private data class ViewBox(
        val minX: Double,
        val minY: Double,
        val width: Double,
        val height: Double,
    ) {
        val maxX: Double get() = minX + width
        val maxY: Double get() = minY + height
    }

    private companion object {
        const val AdaptiveIconForegroundInset = "18dp"
        const val AccessExternalDtdProperty = "http://javax.xml.XMLConstants/property/accessExternalDTD"
        const val AccessExternalSchemaProperty = "http://javax.xml.XMLConstants/property/accessExternalSchema"
        const val AndroidNamespace = "http://schemas.android.com/apk/res/android"
        const val SvgNamespace = "http://www.w3.org/2000/svg"
        const val CatalogAssetPath = "icon_pack/catalog.json"
        const val DefaultIconId = "default"
        const val MaxSvgSizeBytes = 700L * 1024
        const val DefaultIconAuthor = "cognitiveshadows03"
        const val DefaultIconAuthorUrl = "https://github.com/cognitiveshadows03"
        const val IntegratedBackgroundMode = "integrated"
        const val TransparentBackgroundMode = "transparent"
        const val DefaultPathFillColor = "#FF000000"
        const val DefaultTransparentIconBackground = "#FFFFFFFF"
        const val LightContrastingBackground = "#FFFFFFFF"
        const val DarkContrastingBackground = "#FF202124"
        const val ComplexArtworkPathThreshold = 32
        const val ComplexArtworkColorThreshold = 4
        const val IconRasterSize = 1024
        const val DefaultAdaptiveInset = "12"
        const val EdgeAlphaThreshold = 16
        const val EdgeSampleInset = 2
        const val OpaqueEdgeRatio = 0.75
        const val ViewBoxValueCount = 4
        const val FullCanvasCoordinateCount = 8
        const val FullCanvasToleranceRatio = 0.01
        const val ColorChannelMax = 255.0
        const val AlphaChannelShift = 24
        const val RedChannelShift = 16
        const val GreenChannelShift = 8
        const val ColorChannelMask = 0xFF
        const val OpaqueAlpha = 0xFF
        const val RedLuminanceWeight = 0.2126
        const val GreenLuminanceWeight = 0.7152
        const val BlueLuminanceWeight = 0.0722
        const val DarkColorLuminanceThreshold = 0.5
        val WhitespaceOrComma = Regex("[,\\s]+")
        val NumberPattern = Regex("[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][-+]?\\d+)?")
        val ShortRgbColor = Regex("^#[0-9A-Fa-f]{3}$")
        val ShortArgbColor = Regex("^#[0-9A-Fa-f]{4}$")
        val RgbColor = Regex("^#[0-9A-Fa-f]{6}$")
        val ArgbColor = Regex("^#[0-9A-Fa-f]{8}$")
        val PngSignature =
            byteArrayOf(
                0x89.toByte(),
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A,
            )
    }
}
