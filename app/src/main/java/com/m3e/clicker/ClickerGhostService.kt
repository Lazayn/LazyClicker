package com.m3e.clicker

/**
 * 幽灵模式下的常驻服务：和 [ClickerForegroundService] 同一套逻辑，
 * 只是由 Manifest 指定跑在 `:ghost` 进程里，App 被清后台时这个进程仍然活着。
 */
class ClickerGhostService : ClickerForegroundService()
