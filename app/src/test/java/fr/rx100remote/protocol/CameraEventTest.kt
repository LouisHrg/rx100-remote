package fr.rx100remote.protocol

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraEventTest {

    // Shape of a getEvent v1.0 result: fixed slots, null when absent, objects or arrays of objects.
    private val full = JSONArray(
        """
        [
          {"type":"availableApiList","names":["getEvent","actTakePicture","startMovieRec"]},
          {"type":"cameraStatus","cameraStatus":"IDLE"},
          {"type":"zoomInformation","zoomPosition":0},
          null, null, null, null, null, null, null,
          [{"type":"storageInformation","storageID":"Memory Card 1","recordTarget":true,
            "numberOfRecordableImages":1234,"recordableTime":87,"storageDescription":"SD"}],
          null,
          {"type":"shootMode","currentShootMode":"still","shootModeCandidates":["still","movie"]}
        ]
        """.trimIndent(),
    )

    @Test fun parsesFullEvent() {
        val e = CameraEvent.parse(full)
        assertEquals(setOf("getEvent", "actTakePicture", "startMovieRec"), e.apis)
        assertEquals("IDLE", e.cameraStatus)
        assertEquals("still", e.shootMode)
        assertEquals(listOf("still", "movie"), e.shootModeCandidates)
        assertEquals(true, e.hasStorage)
        assertEquals(1234, e.recordableImages)
        assertEquals(87, e.recordableMinutes)
    }

    @Test fun noCardAndRecordTargetPreference() {
        val e = CameraEvent.parse(JSONArray("""[[
            {"type":"storageInformation","storageID":"No Media","recordTarget":false,"numberOfRecordableImages":-1,"recordableTime":-1},
            {"type":"storageInformation","storageID":"Memory Card 1","recordTarget":true,"numberOfRecordableImages":5,"recordableTime":-1}
        ]]"""))
        assertEquals(true, e.hasStorage)
        assertEquals(5, e.recordableImages)
        assertEquals(false, CameraEvent.parse(JSONArray("""[[{"type":"storageInformation","storageID":"No Media"}]]""")).hasStorage)
    }

    @Test fun partialEventMergesOverPrevious() {
        val base = CameraEvent.parse(full)
        val update = CameraEvent.parse(JSONArray("""[null,{"type":"cameraStatus","cameraStatus":"MovieRecording"}]"""))
        val merged = base.merge(update)
        assertEquals("MovieRecording", merged.cameraStatus)
        assertEquals(1234, merged.recordableImages)
        assertEquals("still", merged.shootMode)
    }
}
