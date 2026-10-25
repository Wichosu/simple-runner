package com.wichosu.simplerunner.ui.map

/**
 * The map's single point of contact with the outside world.
 *
 * When the tile source has to change, this is the file that changes. Everything else in
 * the app talks to [RouteMap] and does not know which renderer or provider is behind it.
 */
object MapConfig {

    const val STYLE_URL = "https://tiles.openfreemap.org/styles/positron"

    const val ATTRIBUTION_URL = "https://www.openstreetmap.org/copyright"

    /** Zoom used while following the runner. */
    const val FOLLOW_ZOOM = 16.5

    /** Space kept around a fitted route so the line does not touch the edges. */
    const val FIT_PADDING_PX = 96

    /**
     * Fixed route colours rather than theme colours: the basemap stays light in both app
     * themes, so a deep green with a light casing reads correctly either way.
     */
    val ROUTE_COLOR = 0xFF1E6B4E.toInt()
    val ROUTE_CASING_COLOR = 0xFFF6FBF4.toInt()
}
