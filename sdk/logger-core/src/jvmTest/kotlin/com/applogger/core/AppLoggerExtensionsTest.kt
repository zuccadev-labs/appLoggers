package com.applogger.core

import com.applogger.core.model.LogLevel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AppLoggerExtensionsTest {

    // ── Minimal in-test spy, avoids logger-test circular dep ──────────────────

    data class Capture(
        val level: LogLevel,
        val tag: String,
        val message: String,
        val throwable: Throwable? = null,
        val extra: Map<String, Any>? = null
    )

    data class MetricCapture(
        val name: String,
        val value: Double,
        val unit: String,
        val tags: Map<String, String>?
    )

    class SpyLogger : AppLogger {
        val calls = mutableListOf<Capture>()
        val metrics = mutableListOf<MetricCapture>()
        override fun debug(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) =
            calls.add(Capture(LogLevel.DEBUG, tag, message, throwable, extra)).let {}
        override fun info(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) =
            calls.add(Capture(LogLevel.INFO, tag, message, throwable, extra)).let {}
        override fun warn(tag: String, message: String, throwable: Throwable?, anomalyType: String?, extra: Map<String, Any>?) =
            calls.add(Capture(LogLevel.WARN, tag, message, throwable, extra)).let {}
        override fun error(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) =
            calls.add(Capture(LogLevel.ERROR, tag, message, throwable, extra)).let {}
        override fun critical(tag: String, message: String, throwable: Throwable?, extra: Map<String, Any>?) =
            calls.add(Capture(LogLevel.CRITICAL, tag, message, throwable, extra)).let {}
        override fun metric(name: String, value: Double, unit: String, tags: Map<String, String>?) =
            metrics.add(MetricCapture(name, value, unit, tags)).let {}
        override fun flush() = Unit
    }

    private lateinit var spy: SpyLogger

    @BeforeEach
    fun setup() {
        spy = SpyLogger()
    }

    // ── logTag() ──────────────────────────────────────────────────────────────

    @Test
    fun `logTag returns simple class name`() {
        assertEquals("AppLoggerExtensionsTest", this.logTag())
    }

    @Test
    fun `logTag on anonymous object returns Anonymous`() {
        val anon = object : Any() {}
        assertEquals("Anonymous", anon.logTag())
    }

    // ── AppLogger shorthand extensions ────────────────────────────────────────

    @Test
    fun `logD delegates to debug with correct tag and message`() {
        spy.logD("TAG", "debug message")
        assertEquals(1, spy.calls.size)
        spy.calls[0].also {
            assertEquals(LogLevel.DEBUG, it.level)
            assertEquals("TAG", it.tag)
            assertEquals("debug message", it.message)
            assertNull(it.throwable)
        }
    }

    @Test
    fun `logI delegates to info with throwable`() {
        val e = RuntimeException("info-error")
        spy.logI("TAG", "info message", throwable = e)
        assertEquals(LogLevel.INFO, spy.calls[0].level)
        assertEquals(e, spy.calls[0].throwable)
    }

    @Test
    fun `logW delegates to warn with anomalyType and throwable`() {
        val e = Exception("slow")
        spy.logW("NETWORK", "Slow response", throwable = e, anomalyType = "HIGH_LATENCY")
        assertEquals(LogLevel.WARN, spy.calls[0].level)
        assertEquals(e, spy.calls[0].throwable)
    }

    @Test
    fun `logE delegates to error`() {
        val e = RuntimeException("payment failed")
        spy.logE("PAYMENT", "Transaction error", throwable = e)
        assertEquals(LogLevel.ERROR, spy.calls[0].level)
        assertEquals("payment failed", spy.calls[0].throwable?.message)
    }

    @Test
    fun `logC delegates to critical`() {
        spy.logC("AUTH", "Token refresh failed")
        assertEquals(LogLevel.CRITICAL, spy.calls[0].level)
        assertEquals("AUTH", spy.calls[0].tag)
    }

    @Test
    fun `logD passes extra map`() {
        spy.logD("TAG", "msg", extra = mapOf("key" to "value"))
        assertEquals("value", spy.calls[0].extra?.get("key"))
    }

    // ── Any tag-inferring extensions ──────────────────────────────────────────

    @Test
    fun `Any logD infers tag from class name`() {
        this.logD(spy, "debug via any")
        assertEquals("AppLoggerExtensionsTest", spy.calls[0].tag)
        assertEquals("debug via any", spy.calls[0].message)
        assertEquals(LogLevel.DEBUG, spy.calls[0].level)
    }

    @Test
    fun `Any logI infers tag and captures throwable`() {
        val e = RuntimeException("any-info-error")
        this.logI(spy, "info via any", throwable = e)
        assertEquals("AppLoggerExtensionsTest", spy.calls[0].tag)
        assertEquals(e, spy.calls[0].throwable)
    }

    @Test
    fun `Any logW infers tag with anomalyType and throwable`() {
        val e = Exception("warn via any")
        this.logW(spy, "warn message", throwable = e, anomalyType = "ANOMALY")
        assertEquals("AppLoggerExtensionsTest", spy.calls[0].tag)
        assertEquals(e, spy.calls[0].throwable)
        assertEquals(LogLevel.WARN, spy.calls[0].level)
    }

    @Test
    fun `Any logE infers tag and records error`() {
        val e = RuntimeException("error via any")
        this.logE(spy, "error message", throwable = e)
        assertEquals("AppLoggerExtensionsTest", spy.calls[0].tag)
        assertEquals(e, spy.calls[0].throwable)
        assertEquals(LogLevel.ERROR, spy.calls[0].level)
    }

    @Test
    fun `Any logC infers tag and records critical`() {
        this.logC(spy, "critical message")
        assertEquals("AppLoggerExtensionsTest", spy.calls[0].tag)
        assertEquals("critical message", spy.calls[0].message)
        assertEquals(LogLevel.CRITICAL, spy.calls[0].level)
    }

    @Test
    fun `Any logD with extra map passes metadata`() {
        this.logD(spy, "with extra", extra = mapOf("key" to "value"))
        assertEquals("value", spy.calls[0].extra?.get("key"))
    }

    @Test
    fun `null throwable results in null in captured call`() {
        this.logI(spy, "no exception", throwable = null)
        assertNull(spy.calls[0].throwable)
    }

    // ── logM shorthand ────────────────────────────────────────────────────────

    @Test
    fun `logM delegates to metric with correct fields`() {
        spy.logM("screen_load_time", 320.0, "ms", mapOf("screen" to "Home"))
        assertEquals(1, spy.metrics.size)
        spy.metrics[0].also {
            assertEquals("screen_load_time", it.name)
            assertEquals(320.0, it.value)
            assertEquals("ms", it.unit)
            assertEquals("Home", it.tags?.get("screen"))
        }
    }

    @Test
    fun `logM uses count as default unit`() {
        spy.logM("frame_drop", 3.0)
        assertEquals("count", spy.metrics[0].unit)
    }

    @Test
    fun `logM with null tags passes null to metric`() {
        spy.logM("cpu_usage", 45.0, "percent")
        assertNull(spy.metrics[0].tags)
    }

    // ── Any.logM tag-inferring ─────────────────────────────────────────────────

    @Test
    fun `Any logM infers source tag and merges with provided tags`() {
        this.logM(spy, "render_time", 16.0, "ms", mapOf("frame" to "60fps"))
        assertEquals(1, spy.metrics.size)
        spy.metrics[0].also {
            assertEquals("render_time", it.name)
            assertEquals(16.0, it.value)
            assertEquals("ms", it.unit)
            assertEquals("60fps", it.tags?.get("frame"))
            assertEquals("AppLoggerExtensionsTest", it.tags?.get("source"))
        }
    }

    @Test
    fun `Any logM with null tags still adds source tag`() {
        this.logM(spy, "memory_usage", 128.0, "mb")
        assertEquals("AppLoggerExtensionsTest", spy.metrics[0].tags?.get("source"))
    }

    @Test
    fun `Any logM does not override explicit source tag`() {
        // If caller explicitly passes "source", it should NOT be overridden
        this.logM(spy, "custom_metric", 1.0, "count", mapOf("source" to "CustomSource"))
        assertEquals("CustomSource", spy.metrics[0].tags?.get("source"))
    }

    // ── loggerTag<T>() ────────────────────────────────────────────────────────

    @Test
    fun `loggerTag returns simple class name of type parameter`() {
        val tag = loggerTag<AppLoggerExtensionsTest>()
        assertEquals("AppLoggerExtensionsTest", tag)
    }

    @Test
    fun `loggerTag can be used as a constant in companion-style pattern`() {
        val tag = loggerTag<SpyLogger>()
        assertEquals("SpyLogger", tag)
        spy.logI(tag, "using companion tag")
        assertEquals("SpyLogger", spy.calls[0].tag)
    }

    // ── withTag() ─────────────────────────────────────────────────────────────

    @Test
    fun `withTag string fixes tag for all calls`() {
        val log = spy.withTag("PaymentRepository")
        log.i("Charging card")
        log.e("Charge failed", RuntimeException("declined"))
        assertEquals(2, spy.calls.size)
        assertTrue(spy.calls.all { it.tag == "PaymentRepository" })
    }

    @Test
    fun `withTag receiver infers tag from class name`() {
        val log = spy.withTag(this)
        log.d("debug msg")
        assertEquals("AppLoggerExtensionsTest", spy.calls[0].tag)
    }

    @Test
    fun `TaggedLogger metric delegates correctly`() {
        val log = spy.withTag("Repo")
        log.metric("query_time", 50.0, "ms", mapOf("table" to "users"))
        assertEquals(1, spy.metrics.size)
        assertEquals("query_time", spy.metrics[0].name)
        assertEquals("users", spy.metrics[0].tags?.get("table"))
    }

    @Test
    fun `TaggedLogger warn passes anomalyType`() {
        val log = spy.withTag("Network")
        log.w("Slow response", anomalyType = "HIGH_LATENCY")
        assertEquals(LogLevel.WARN, spy.calls[0].level)
        assertEquals("Network", spy.calls[0].tag)
    }

    // ── timed() ───────────────────────────────────────────────────────────────

    @Test
    fun `timed records metric with elapsed time`() {
        val result = spy.timed("db_query", "ms") { 42 }
        assertEquals(42, result)
        assertEquals(1, spy.metrics.size)
        assertEquals("db_query", spy.metrics[0].name)
        assertEquals("ms", spy.metrics[0].unit)
        assertTrue(spy.metrics[0].value >= 0.0)
    }

    @Test
    fun `timed passes tags to metric`() {
        spy.timed("api_call", tags = mapOf("endpoint" to "/users")) { Unit }
        assertEquals("/users", spy.metrics[0].tags?.get("endpoint"))
    }

    @Test
    fun `Any timed infers source tag`() {
        this.timed(spy, "render_time") { Unit }
        assertEquals("AppLoggerExtensionsTest", spy.metrics[0].tags?.get("source"))
    }

    @Test
    fun `Any timed does not override explicit source tag`() {
        this.timed(spy, "render_time", tags = mapOf("source" to "Custom")) { Unit }
        assertEquals("Custom", spy.metrics[0].tags?.get("source"))
    }

    // ── logCatching() ─────────────────────────────────────────────────────────

    @Test
    fun `logCatching returns result on success`() {
        val result = spy.logCatching("TAG", "fetch user") { "user_data" }
        assertEquals("user_data", result)
        assertTrue(spy.calls.isEmpty())
    }

    @Test
    fun `logCatching returns null and logs error on exception`() {
        val result = spy.logCatching("TAG", "fetch user") {
            throw RuntimeException("timeout")
        }
        assertNull(result)
        assertEquals(1, spy.calls.size)
        assertEquals(LogLevel.ERROR, spy.calls[0].level)
        assertEquals("TAG", spy.calls[0].tag)
        assertTrue(spy.calls[0].message.contains("fetch user"))
        assertNotNull(spy.calls[0].throwable)
    }

    @Test
    fun `Any logCatching infers tag from class name`() {
        val result = this.logCatching(spy, "submit order") {
            throw IllegalStateException("out of stock")
        }
        assertNull(result)
        assertEquals("AppLoggerExtensionsTest", spy.calls[0].tag)
        assertEquals(LogLevel.ERROR, spy.calls[0].level)
    }

    @Test
    fun `logCatching passes extra to error event`() {
        spy.logCatching("TAG", "process", extra = mapOf("order_id" to "123")) {
            throw RuntimeException("failed")
        }
        assertEquals("123", spy.calls[0].extra?.get("order_id"))
    }
}
