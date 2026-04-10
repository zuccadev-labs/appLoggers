package com.applogger.core.internal

import com.applogger.core.BatchKind
import com.applogger.core.currentTimeMillis
import com.applogger.core.model.DeviceInfo
import com.applogger.core.model.LogEvent
import com.applogger.core.model.LogLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BatchIntegrityManagerTest {

    private val testDeviceInfo = DeviceInfo(
        brand = "Test",
        model = "Device",
        osVersion = "14",
        apiLevel = 34,
        platform = "ANDROID_MOBILE",
        appVersion = "1.0.0",
        appBuild = 1,
        isLowRamDevice = false,
        connectionType = "wifi"
    )

    @Test
    fun `prepareBatch assigns a single batch id to all events and computes hash`() {
        val manager = BatchIntegrityManager("secret")
        val packet = manager.prepareBatch(
            listOf(buildEvent(id = "b"), buildEvent(id = "a")),
            BatchKind.LOGS
        )

        assertTrue(packet.batchId.isNotBlank())
        assertTrue(packet.events.all { it.batchId == packet.batchId })
        assertTrue(packet.hash.isNotBlank())
    }

    @Test
    fun `prepareBatch is order-independent for hash generation`() {
        val manager = BatchIntegrityManager("secret")

        val packetA = manager.prepareBatch(listOf(buildEvent(id = "b"), buildEvent(id = "a")), BatchKind.LOGS)
        val packetB = manager.prepareBatch(listOf(buildEvent(id = "a"), buildEvent(id = "b")), BatchKind.LOGS)

        assertEquals(packetA.hash, packetB.hash)
        assertNotEquals(packetA.batchId, packetB.batchId)
    }

    @Test
    fun `blank secret disables hash generation`() {
        val manager = BatchIntegrityManager("")
        val packet = manager.prepareBatch(listOf(buildEvent()), BatchKind.LOGS)

        assertNotNull(packet.batchId)
        assertEquals("", packet.hash)
    }

    @Test
    fun `metric batches use metric canonical fields and carry key id`() {
        val manager = BatchIntegrityManager("secret", "10042026")
        val packet = manager.prepareBatch(
            listOf(
                buildEvent(id = "metric-1", level = LogLevel.METRIC, metricName = "frame_drop", metricValue = 3.0, metricUnit = "count"),
                buildEvent(id = "metric-2", level = LogLevel.METRIC, metricName = "latency", metricValue = 18.5, metricUnit = "ms")
            ),
            BatchKind.METRICS
        )

        assertEquals(BatchKind.METRICS, packet.kind)
        assertEquals("10042026", packet.keyId)
        assertTrue(packet.hash.isNotBlank())
    }

    private fun buildEvent(
        id: String = "evt-1",
        timestamp: Long = 1_710_000_000_000L,
        level: LogLevel = LogLevel.INFO,
        metricName: String? = null,
        metricValue: Double? = null,
        metricUnit: String? = null
    ) = LogEvent(
        id = id,
        timestamp = timestamp,
        level = level,
        tag = "TEST",
        message = "message",
        deviceInfo = testDeviceInfo,
        sessionId = "session-1",
        metricName = metricName,
        metricValue = metricValue,
        metricUnit = metricUnit
    )
}