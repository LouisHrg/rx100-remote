package fr.rx100remote.protocol

import org.json.JSONArray
import org.json.JSONObject

/**
 * The parts of a getEvent result we use. Null = not present in this event
 * (getEvent(true) only reports what changed, so callers merge with the previous state).
 *
 * The result is an array whose slots hold either an object or an array of objects,
 * each tagged with "type". We dispatch on "type" rather than on slot index, because
 * slot positions differ between API versions and models.
 */
data class CameraEvent(
    val apis: Set<String>? = null,
    val cameraStatus: String? = null,
    val shootMode: String? = null,
    val shootModeCandidates: List<String>? = null,
    /** False when the camera reports no card. */
    val hasStorage: Boolean? = null,
    /** Remaining shots on the record target; -1 = unknown. */
    val recordableImages: Int? = null,
    /** Remaining movie time in minutes; -1 = unknown. */
    val recordableMinutes: Int? = null,
) {
    /** Overlays the fields present in [newer] on this state. */
    fun merge(newer: CameraEvent) = CameraEvent(
        apis = newer.apis ?: apis,
        cameraStatus = newer.cameraStatus ?: cameraStatus,
        shootMode = newer.shootMode ?: shootMode,
        shootModeCandidates = newer.shootModeCandidates ?: shootModeCandidates,
        hasStorage = newer.hasStorage ?: hasStorage,
        recordableImages = newer.recordableImages ?: recordableImages,
        recordableMinutes = newer.recordableMinutes ?: recordableMinutes,
    )

    companion object {
        fun parse(result: JSONArray): CameraEvent {
            val objects = (0 until result.length()).flatMap { i ->
                when (val slot = result.opt(i)) {
                    is JSONObject -> listOf(slot)
                    is JSONArray -> (0 until slot.length()).mapNotNull { slot.optJSONObject(it) }
                    else -> emptyList()
                }
            }
            fun of(type: String) = objects.firstOrNull { it.optString("type") == type }

            // One entry per card slot; prefer the one marked as record target.
            val storages = objects.filter { it.optString("type") == "storageInformation" }
            val storage = storages.firstOrNull { it.optBoolean("recordTarget") } ?: storages.firstOrNull()

            return CameraEvent(
                apis = of("availableApiList")?.optJSONArray("names")?.strings()?.toSet(),
                cameraStatus = of("cameraStatus")?.optString("cameraStatus")?.ifEmpty { null },
                shootMode = of("shootMode")?.optString("currentShootMode")?.ifEmpty { null },
                shootModeCandidates = of("shootMode")?.optJSONArray("shootModeCandidates")?.strings(),
                hasStorage = storage?.optString("storageID")?.let { it.isNotEmpty() && it != "No Media" },
                recordableImages = storage?.optInt("numberOfRecordableImages", -1),
                recordableMinutes = storage?.optInt("recordableTime", -1),
            )
        }

        private fun JSONArray.strings() = (0 until length()).map { getString(it) }
    }
}
