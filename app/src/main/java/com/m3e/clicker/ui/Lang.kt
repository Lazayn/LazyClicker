package com.m3e.clicker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** 当前语言："zh" / "en" */
val LocalLang = staticCompositionLocalOf { "zh" }

@Composable
fun s(zh: String, en: String): String = if (LocalLang.current == "en") en else zh
