package com.m3e.clicker

import java.util.concurrent.TimeUnit

/** 极简 shell 执行器：在 shell 身份的 UserService 里执行命令并收集输出 */
object Shell {

    fun run(args: List<String>, timeoutMs: Long): String {
        var process: Process? = null
        val builder = StringBuilder()
        try {
            process = ProcessBuilder(args).redirectErrorStream(true).start()
            val worker = Thread {
                try {
                    process.inputStream.bufferedReader().forEachLine { builder.appendLine(it) }
                } catch (_: Throwable) {
                }
            }
            worker.isDaemon = true
            worker.start()
            worker.join(timeoutMs)
        } catch (_: Throwable) {
        } finally {
            try {
                process?.destroy()
                process?.waitFor(200, TimeUnit.MILLISECONDS)
            } catch (_: Throwable) {
            }
        }
        return builder.toString()
    }

    fun hasGetevent(): Boolean = try {
        java.io.File("/system/bin/getevent").exists()
    } catch (_: Throwable) {
        false
    }
}
