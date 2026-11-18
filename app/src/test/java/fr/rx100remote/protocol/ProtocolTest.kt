package fr.rx100remote.protocol

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProtocolTest {

    @Test fun ssdpLocation() {
        val resp = "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nEXT:\r\n" +
            "LOCATION: http://192.168.122.1:64321/DmsRmtDesc.xml\r\nSERVER: UPnP/1.0 SonyImagingDevice/1.0\r\n" +
            "ST: urn:schemas-sony-com:service:ScalarWebAPI:1\r\nUSN: uuid:000::urn:schemas-sony-com:service:ScalarWebAPI:1\r\n\r\n"
        assertEquals("http://192.168.122.1:64321/DmsRmtDesc.xml", Ssdp.parseLocation(resp))
        assertNull(Ssdp.parseLocation(resp.replace("ScalarWebAPI:1\r\nUSN", "Other:1\r\nUSN")))
        assertTrue(Ssdp.searchMessage().contains("ST: ${Ssdp.ST}\r\n"))
        assertTrue(Ssdp.searchMessage().endsWith("\r\n\r\n"))
    }

    @Test fun ssdpDescription() {
        val xml = """
            <root xmlns="urn:schemas-upnp-org:device-1-0"><device>
            <av:X_ScalarWebAPI_DeviceInfo xmlns:av="urn:schemas-sony-com:av">
              <av:X_ScalarWebAPI_Version>1.0</av:X_ScalarWebAPI_Version>
              <av:X_ScalarWebAPI_ServiceList>
                <av:X_ScalarWebAPI_Service>
                  <av:X_ScalarWebAPI_ServiceType>guide</av:X_ScalarWebAPI_ServiceType>
                  <av:X_ScalarWebAPI_ActionList_URL>http://192.168.122.1:8080/sony</av:X_ScalarWebAPI_ActionList_URL>
                </av:X_ScalarWebAPI_Service>
                <av:X_ScalarWebAPI_Service>
                  <av:X_ScalarWebAPI_ServiceType>camera</av:X_ScalarWebAPI_ServiceType>
                  <av:X_ScalarWebAPI_ActionList_URL> http://192.168.122.1:8080/sony/ </av:X_ScalarWebAPI_ActionList_URL>
                </av:X_ScalarWebAPI_Service>
              </av:X_ScalarWebAPI_ServiceList>
            </av:X_ScalarWebAPI_DeviceInfo></device></root>
        """.trimIndent()
        assertEquals(
            mapOf("guide" to "http://192.168.122.1:8080/sony/guide", "camera" to "http://192.168.122.1:8080/sony/camera"),
            Ssdp.parseServices(xml),
        )
    }

    @Test fun jsonRpcRequest() {
        val json = JSONObject(CameraApi.buildRequest("actZoom", JSONArray(listOf("in", "1shot")), 7, "1.0"))
        assertEquals("actZoom", json.getString("method"))
        assertEquals("in", json.getJSONArray("params").getString(0))
        assertEquals(7, json.getInt("id"))
        assertEquals("1.0", json.getString("version"))
    }

    @Test fun jsonRpcResponses() {
        val r = CameraApi.parseResponse("""{"result":[["getVersions","startRecMode","getAvailableApiList"]],"id":1}""")
        assertEquals(setOf("getVersions", "startRecMode", "getAvailableApiList"), CameraApi.parseApiList(r))
        assertEquals("http://x/liveview", CameraApi.parseResponse("""{"result":["http://x/liveview"],"id":2}""").getString(0))
        assertEquals(1, CameraApi.parseResponse("""{"results":[[1]],"id":3}""").length())
        try {
            CameraApi.parseResponse("""{"error":[40403,"Long shooting"],"id":4}""")
            fail()
        } catch (e: CameraApiException) {
            assertEquals(40403, e.code)
        }
    }

    @Test fun defaultEndpoints() {
        assertEquals("http://192.168.122.1:8080/sony/camera", CameraApi.defaultEndpoints("192.168.122.1:8080/sony/")["camera"])
    }
}
