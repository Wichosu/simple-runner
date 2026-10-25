package com.wichosu.simplerunner.ui.map

import com.wichosu.simplerunner.data.local.TrackPointEntity

/** The only shape the map needs from the tracking model. */
data class RoutePoint(
    val lat: Double,
    val lon: Double,
    val segment: Int,
)

fun TrackPointEntity.toRoutePoint(): RoutePoint = RoutePoint(
    lat = lat,
    lon = lon,
    segment = segment,
)
