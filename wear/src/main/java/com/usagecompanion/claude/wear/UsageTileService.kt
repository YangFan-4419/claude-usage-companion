package com.usagecompanion.claude.wear

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.tiles.ColorBuilders
import androidx.wear.tiles.DimensionBuilders
import androidx.wear.tiles.LayoutElementBuilders
import androidx.wear.tiles.ModifiersBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.tiles.TimelineBuilders
import com.google.common.util.concurrent.ListenableFuture

class UsageTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        return CallbackToFutureAdapter.getFuture { completer ->
            completer.set(buildTile(WearSnapshotStore(this).read()))
            "usage-tile"
        }
    }

    override fun onResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        return CallbackToFutureAdapter.getFuture { completer ->
            completer.set(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())
            "usage-tile-resources"
        }
    }

    private fun buildTile(snapshot: WearSnapshot): TileBuilders.Tile {
        val layout = LayoutElementBuilders.Layout.Builder()
            .setRoot(tileContent(snapshot))
            .build()

        val timeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(layout)
                    .build(),
            )
            .build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTimeline(timeline)
            .setFreshnessIntervalMillis(60_000L)
            .build()
    }

    private fun tileContent(snapshot: WearSnapshot): LayoutElementBuilders.LayoutElement {
        val fiveHourProgress = if (snapshot.hasUsage) snapshot.fiveHourPercent else 0
        val sevenDayProgress = if (snapshot.hasUsage) snapshot.sevenDayPercent else 0
        val title = if (snapshot.hasUsage) "Claude Usage" else "Set up"
        val primary = if (snapshot.hasUsage) "${snapshot.fiveHourPercent}%" else "--"
        val label = if (snapshot.hasUsage) "5h used" else "Open phone app"
        val secondary = if (snapshot.hasUsage && snapshot.tileShowsSevenDay) {
            "7d ${snapshot.sevenDayPercent}%"
        } else {
            ""
        }
        val reset = if (snapshot.hasUsage) {
            if (snapshot.tileShowsSevenDay) {
                "${snapshot.fiveHourResetLabel} / ${snapshot.sevenDayResetLabel}"
            } else {
                "reset ${snapshot.fiveHourResetLabel}"
            }
        } else {
            "OAuth token required"
        }

        val content = LayoutElementBuilders.Column.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.wrap())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text(title, 13f, COLOR_ON_SURFACE, LayoutElementBuilders.FONT_WEIGHT_MEDIUM))
            .addContent(text(primary, 38f, COLOR_ON_SURFACE, LayoutElementBuilders.FONT_WEIGHT_BOLD))
            .addContent(text(label, 11f, COLOR_ON_SURFACE_VARIANT, LayoutElementBuilders.FONT_WEIGHT_NORMAL))
            .apply {
                if (secondary.isNotEmpty()) {
                    addContent(text(secondary, 16f, COLOR_SECONDARY, LayoutElementBuilders.FONT_WEIGHT_BOLD))
                }
            }
            .addContent(text(reset, 10f, COLOR_ON_SURFACE_VARIANT, LayoutElementBuilders.FONT_WEIGHT_NORMAL))
            .build()

        val root = LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(tileModifiers())
            .addContent(ringLayer(COLOR_TRACK, 100, paddingDp = 10f, thicknessDp = 10f))
            .addContent(ringLayer(colorFor(fiveHourProgress), fiveHourProgress, paddingDp = 10f, thicknessDp = 10f))

        if (snapshot.tileShowsSevenDay) {
            root
                .addContent(ringLayer(COLOR_TRACK_INNER, 100, paddingDp = 31f, thicknessDp = 8f))
                .addContent(ringLayer(colorFor(sevenDayProgress, fallback = COLOR_SECONDARY), sevenDayProgress, paddingDp = 31f, thicknessDp = 8f))
        }

        return root.addContent(content).build()
    }

    private fun ringLayer(
        color: Int,
        percent: Int,
        paddingDp: Float,
        thicknessDp: Float,
    ): LayoutElementBuilders.Box {
        return LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setAll(DimensionBuilders.dp(paddingDp))
                            .build(),
                    )
                    .build(),
            )
            .addContent(progressArc(color, percent, thicknessDp))
            .build()
    }

    private fun progressArc(color: Int, percent: Int, thicknessDp: Float): LayoutElementBuilders.Arc {
        return LayoutElementBuilders.Arc.Builder()
            .setAnchorAngle(DimensionBuilders.degrees(PROGRESS_START_ANGLE))
            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
            .addContent(
                LayoutElementBuilders.ArcLine.Builder()
                    .setLength(DimensionBuilders.degrees(PROGRESS_SWEEP * percent.coerceIn(0, 100) / 100f))
                    .setThickness(DimensionBuilders.dp(thicknessDp))
                    .setColor(ColorBuilders.argb(color))
                    .build(),
            )
            .build()
    }

    private fun tileModifiers(): ModifiersBuilders.Modifiers {
        return ModifiersBuilders.Modifiers.Builder()
            .setBackground(
                ModifiersBuilders.Background.Builder()
                    .setColor(ColorBuilders.argb(COLOR_BACKGROUND))
                    .build(),
            )
            .build()
    }

    private fun text(
        value: String,
        size: Float,
        color: Int,
        weight: Int,
    ): LayoutElementBuilders.Text {
        return LayoutElementBuilders.Text.Builder()
            .setText(value)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.sp(size))
                    .setColor(ColorBuilders.argb(color))
                    .setWeight(weight)
                    .build(),
            )
            .setMaxLines(1)
            .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
            .build()
    }

    private fun colorFor(percent: Int, fallback: Int = COLOR_PRIMARY): Int {
        return when {
            percent <= 0 -> fallback
            percent > 100 -> COLOR_ERROR
            percent >= 90 -> COLOR_DEEP_ORANGE
            percent >= 70 -> COLOR_TERTIARY
            else -> fallback
        }
    }

    companion object {
        private const val RESOURCES_VERSION = "6"
        private const val COLOR_BACKGROUND = 0xFF000000.toInt()
        private const val COLOR_PRIMARY = 0xFFD97250.toInt()
        private const val COLOR_SECONDARY = 0xFFE7B8A4.toInt()
        private const val COLOR_TERTIARY = 0xFFFBBC04.toInt()
        private const val COLOR_DEEP_ORANGE = 0xFFF97316.toInt()
        private const val COLOR_ERROR = 0xFFEF4444.toInt()
        private const val COLOR_OUTLINE = 0xFF9CA3AF.toInt()
        private const val COLOR_TRACK = 0xFF2B2B2B.toInt()
        private const val COLOR_TRACK_INNER = 0xFF1F1F1F.toInt()
        private const val COLOR_ON_SURFACE = 0xFFFFFFFF.toInt()
        private const val COLOR_ON_SURFACE_VARIANT = 0xFFC9C3BF.toInt()
        private const val PROGRESS_START_ANGLE = 0f
        private const val PROGRESS_SWEEP = 360f
    }
}
