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
        val title = if (snapshot.hasUsage) "Claude Usage" else "Set up"
        val plan = if (snapshot.hasUsage) snapshot.planLabel else "Open phone app"
        val primary = if (snapshot.hasUsage) "${snapshot.fiveHourPercent}%" else "--"
        val secondary = if (snapshot.hasUsage) {
            "5h used · 7d ${snapshot.sevenDayPercent}%"
        } else {
            "OAuth token required"
        }
        val reset = if (snapshot.hasUsage) {
            "reset ${snapshot.fiveHourResetLabel}"
        } else {
            ""
        }

        return LayoutElementBuilders.Column.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setModifiers(background(COLOR_BACKGROUND))
            .addContent(text(title, 13f, COLOR_ON_SURFACE, LayoutElementBuilders.FONT_WEIGHT_MEDIUM))
            .addContent(text(plan, 10f, COLOR_PRIMARY, LayoutElementBuilders.FONT_WEIGHT_NORMAL))
            .addContent(text(primary, 40f, colorFor(snapshot), LayoutElementBuilders.FONT_WEIGHT_BOLD))
            .addContent(text(secondary, 11f, COLOR_ON_SURFACE_VARIANT, LayoutElementBuilders.FONT_WEIGHT_NORMAL))
            .addContent(text(reset, 10f, COLOR_ON_SURFACE_VARIANT, LayoutElementBuilders.FONT_WEIGHT_NORMAL))
            .build()
    }

    private fun background(color: Int): ModifiersBuilders.Modifiers {
        return ModifiersBuilders.Modifiers.Builder()
            .setBackground(
                ModifiersBuilders.Background.Builder()
                    .setColor(ColorBuilders.argb(color))
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

    private fun colorFor(snapshot: WearSnapshot): Int {
        return when {
            !snapshot.hasUsage -> COLOR_OUTLINE
            snapshot.fiveHourPercent > 100 || snapshot.sevenDayPercent > 100 -> COLOR_ERROR
            snapshot.fiveHourPercent >= 90 || snapshot.sevenDayPercent >= 90 -> COLOR_DEEP_ORANGE
            snapshot.fiveHourPercent >= 70 || snapshot.sevenDayPercent >= 70 -> COLOR_TERTIARY
            else -> COLOR_PRIMARY
        }
    }

    companion object {
        private const val RESOURCES_VERSION = "1"
        private const val COLOR_BACKGROUND = 0xFF000000.toInt()
        private const val COLOR_PRIMARY = 0xFFD97250.toInt()
        private const val COLOR_TERTIARY = 0xFFFBBC04.toInt()
        private const val COLOR_DEEP_ORANGE = 0xFFF97316.toInt()
        private const val COLOR_ERROR = 0xFFEF4444.toInt()
        private const val COLOR_OUTLINE = 0xFF9CA3AF.toInt()
        private const val COLOR_ON_SURFACE = 0xFFFFFFFF.toInt()
        private const val COLOR_ON_SURFACE_VARIANT = 0xFFC9C3BF.toInt()
    }
}
