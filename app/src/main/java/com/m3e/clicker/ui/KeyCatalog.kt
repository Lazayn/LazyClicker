package com.m3e.clicker.ui

import android.view.KeyEvent

/** 常用键值：Android 的 keyCode 与显示名 */
data class KeyOption(val code: Int, val label: String)

val COMMON_KEYS: List<KeyOption> = listOf(
    KeyOption(KeyEvent.KEYCODE_ENTER, "Enter"),
    KeyOption(KeyEvent.KEYCODE_SPACE, "Space"),
    KeyOption(KeyEvent.KEYCODE_ESCAPE, "Esc"),
    KeyOption(KeyEvent.KEYCODE_TAB, "Tab"),
    KeyOption(KeyEvent.KEYCODE_SHIFT_LEFT, "Shift"),
    KeyOption(KeyEvent.KEYCODE_CTRL_LEFT, "Ctrl"),
    KeyOption(KeyEvent.KEYCODE_ALT_LEFT, "Alt"),
    KeyOption(KeyEvent.KEYCODE_A, "A"),
    KeyOption(KeyEvent.KEYCODE_D, "D"),
    KeyOption(KeyEvent.KEYCODE_E, "E"),
    KeyOption(KeyEvent.KEYCODE_F, "F"),
    KeyOption(KeyEvent.KEYCODE_Q, "Q"),
    KeyOption(KeyEvent.KEYCODE_W, "W"),
    KeyOption(KeyEvent.KEYCODE_S, "S"),
    KeyOption(KeyEvent.KEYCODE_Z, "Z"),
    KeyOption(KeyEvent.KEYCODE_X, "X"),
    KeyOption(KeyEvent.KEYCODE_1, "1"),
    KeyOption(KeyEvent.KEYCODE_2, "2"),
    KeyOption(KeyEvent.KEYCODE_3, "3"),
    KeyOption(KeyEvent.KEYCODE_DPAD_CENTER, "DpadCenter"),
)

private val KEY_NAMES: Map<Int, String> = mapOf(
    KeyEvent.KEYCODE_UNKNOWN to "Unknown",
    KeyEvent.KEYCODE_DPAD_CENTER to "DpadCenter",
    KeyEvent.KEYCODE_DPAD_UP to "DpadUp",
    KeyEvent.KEYCODE_DPAD_DOWN to "DpadDown",
    KeyEvent.KEYCODE_DPAD_LEFT to "DpadLeft",
    KeyEvent.KEYCODE_DPAD_RIGHT to "DpadRight",
    KeyEvent.KEYCODE_SOFT_LEFT to "SoftLeft",
    KeyEvent.KEYCODE_SOFT_RIGHT to "SoftRight",
    KeyEvent.KEYCODE_HOME to "Home",
    KeyEvent.KEYCODE_BACK to "Back",
    KeyEvent.KEYCODE_CALL to "Call",
    KeyEvent.KEYCODE_0 to "0",
    KeyEvent.KEYCODE_1 to "1",
    KeyEvent.KEYCODE_2 to "2",
    KeyEvent.KEYCODE_3 to "3",
    KeyEvent.KEYCODE_4 to "4",
    KeyEvent.KEYCODE_5 to "5",
    KeyEvent.KEYCODE_6 to "6",
    KeyEvent.KEYCODE_7 to "7",
    KeyEvent.KEYCODE_8 to "8",
    KeyEvent.KEYCODE_9 to "9",
    KeyEvent.KEYCODE_STAR to "Star",
    KeyEvent.KEYCODE_POUND to "Pound",
)

fun keyLabel(code: Int): String {
    val known = KEY_NAMES[code]
    if (known != null) return known
    val alphabet = KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z
    if (code in alphabet) {
        return ('A' + (code - KeyEvent.KEYCODE_A)).toString()
    }
    val named = when (code) {
        KeyEvent.KEYCODE_ENTER -> "Enter"
        KeyEvent.KEYCODE_SPACE -> "Space"
        KeyEvent.KEYCODE_TAB -> "Tab"
        KeyEvent.KEYCODE_ESCAPE -> "Esc"
        KeyEvent.KEYCODE_SHIFT_LEFT -> "Shift"
        KeyEvent.KEYCODE_CTRL_LEFT -> "Ctrl"
        KeyEvent.KEYCODE_ALT_LEFT -> "Alt"
        KeyEvent.KEYCODE_DEL -> "Del"
        KeyEvent.KEYCODE_VOLUME_UP -> "VolUp"
        KeyEvent.KEYCODE_VOLUME_DOWN -> "VolDown"
        else -> null
    }
    if (named != null) return named
    return "Key $code"
}

fun keyDisplay(code: Int): String = "${keyLabel(code)}  ($code)"
