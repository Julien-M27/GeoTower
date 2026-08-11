package fr.geotower.data.trip

import fr.geotower.data.api.RouteApi

internal fun step(
    latitude: Double,
    longitude: Double,
    label: String = "Étape",
    kind: String = TripStep.KIND_MANUAL,
    visitedAtMillis: Long? = null
) = TripStep(
    latitude = latitude,
    longitude = longitude,
    label = label,
    kind = kind,
    supportId = null,
    visitedAtMillis = visitedAtMillis,
    note = null,
    profileToNext = null
)

internal fun leg(fromIndex: Int, toIndex: Int, distanceMeters: Double = 1_000.0) = TripLeg(
    fromIndex = fromIndex,
    toIndex = toIndex,
    profile = RouteApi.PROFILE_CAR,
    distanceMeters = distanceMeters,
    durationSeconds = 60.0,
    encodedGeometry = "",
    maneuvers = null
)

internal fun plan(
    steps: List<TripStep>,
    legs: List<TripLeg> = emptyList(),
    returnToStart: Boolean = false,
    profile: String = RouteApi.PROFILE_CAR
) = TripPlan(
    id = "test",
    schemaVersion = TripPlanStore.SCHEMA_VERSION,
    name = "Tournée",
    createdAtMillis = 0L,
    updatedAtMillis = 0L,
    profile = profile,
    returnToStart = returnToStart,
    steps = steps,
    legs = legs,
    plannedAtMillis = null,
    reminderOffsetsMinutes = emptyList(),
    stopDurationMinutes = 0,
    status = TripPlan.STATUS_DRAFT
)

/** Étapes régulièrement espacées, pour les tests de découpe où la position n'importe pas. */
internal fun ladder(count: Int): List<TripStep> =
    List(count) { step(48.80 + it * 0.01, 2.30 + it * 0.01, label = "Étape $it") }
