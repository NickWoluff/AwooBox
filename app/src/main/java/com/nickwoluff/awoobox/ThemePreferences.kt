package com.nickwoluff.awoobox

import android.content.Context
import androidx.core.content.edit

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色模式"),
    DARK("深色模式"),
}

enum class PaletteStyle(val label: String, val description: String) {
    TONAL_SPOT("Tonal Spot", "柔和主色与中性表面"),
    VIBRANT("Vibrant", "高饱和、活泼"),
    EXPRESSIVE("Expressive", "强烈色彩对比"),
    NEUTRAL("Neutral", "低饱和、克制"),
}

enum class SeedColor(val label: String, val argb: Int) {
    AWO("嗷呜绿", 0xFF2F6566.toInt()),
    OCEAN("海盐蓝", 0xFF146C94.toInt()),
    FOREST("苔原绿", 0xFF2F6B4F.toInt()),
    LILAC("丁香紫", 0xFF725572.toInt()),
    ROSE("暖玫瑰", 0xFF8B4A5A.toInt()),
}

data class ThemePreferences(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val paletteStyle: PaletteStyle = PaletteStyle.TONAL_SPOT,
    val seedColor: SeedColor = SeedColor.AWO,
    val predictiveBack: Boolean = true,
)

class ThemeStore(context: Context) {
    private val prefs = context.getSharedPreferences("awoo_theme", Context.MODE_PRIVATE)

    fun load(): ThemePreferences {
        return ThemePreferences(
            mode = ThemeMode.entries.getOrElse(prefs.getInt("mode", 0)) { ThemeMode.SYSTEM },
            dynamicColor = prefs.getBoolean("dynamic_color", true),
            paletteStyle = PaletteStyle.entries.getOrElse(prefs.getInt("palette_style", 0)) { PaletteStyle.TONAL_SPOT },
            seedColor = SeedColor.entries.getOrElse(prefs.getInt("seed_color", 0)) { SeedColor.AWO },
            predictiveBack = prefs.getBoolean("predictive_back", true),
        )
    }

    fun save(value: ThemePreferences) {
        prefs.edit {
            putInt("mode", value.mode.ordinal)
            putBoolean("dynamic_color", value.dynamicColor)
            putInt("palette_style", value.paletteStyle.ordinal)
            putInt("seed_color", value.seedColor.ordinal)
            putBoolean("predictive_back", value.predictiveBack)
        }
    }
}
