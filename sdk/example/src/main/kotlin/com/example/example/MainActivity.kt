package com.example.example

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.applogger.core.AppLoggerSDK

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // SDK is initialized by ExampleApp (Application.onCreate).
        // Exercise every API so the example APK writes real data to Supabase.
        val useCases = ExampleUseCases(AppLoggerSDK)

        val log = StringBuilder()

        fun record(label: String, block: () -> Unit) {
            runCatching(block)
                .onSuccess { log.appendLine("checkmark $label") }
                .onFailure { log.appendLine("x $label: ${it.message}") }
        }

        record("debug")       { useCases.exampleDebug() }
        record("info")        { useCases.exampleInfo() }
        record("warn")        { useCases.exampleWarn() }
        record("error")       { useCases.exampleError() }
        record("critical")    { useCases.exampleCritical() }
        record("metric")      { useCases.exampleMetric() }
        record("withTag")     { useCases.exampleWithTag() }
        record("timed")       { useCases.exampleTimed() }
        record("logCatching") { useCases.exampleLogCatching() }
        record("globalExtra") { useCases.exampleGlobalExtra() }

        val output = TextView(this).apply {
            text = "AppLoggers Example\n\n$log"
            textSize = 14f
            setPadding(32, 32, 32, 32)
        }

        setContentView(ScrollView(this).apply {
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(output)
            })
        })
    }
}
