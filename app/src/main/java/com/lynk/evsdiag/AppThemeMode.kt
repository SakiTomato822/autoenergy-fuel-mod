package com.lynk.dvrprobe

internal enum class AppThemeMode(val label: String) {
    SYSTEM("随系统"), LIGHT("浅色"), DARK("深色");

    fun isLight(systemNight: Boolean): Boolean = when (this) {
        SYSTEM -> !systemNight
        LIGHT -> true
        DARK -> false
    }

    fun next(): AppThemeMode = when (this) {
        SYSTEM -> LIGHT
        LIGHT -> DARK
        DARK -> SYSTEM
    }

    companion object {
        fun fromStored(value: String?): AppThemeMode = values().firstOrNull { it.name == value } ?: SYSTEM
    }
}
