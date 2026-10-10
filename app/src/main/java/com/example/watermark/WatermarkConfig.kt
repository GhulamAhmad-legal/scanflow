package com.example.watermark

import org.json.JSONObject

enum class WatermarkType { TEXT, IMAGE }
enum class WatermarkLayout { SINGLE_CENTER, TILED }
enum class WatermarkScope { ALL_PAGES, CURRENT_PAGE }

data class WatermarkConfig(
    val enabled: Boolean = false,
    val type: WatermarkType = WatermarkType.TEXT,
    val text: String = "CONFIDENTIAL",
    val imagePath: String? = null,
    val fontSizeSp: Float = 36f,
    val colorHex: String = "#888888",
    val opacity: Float = 0.35f,
    val rotationDegrees: Float = -45f,
    val layout: WatermarkLayout = WatermarkLayout.TILED,
    val applyScope: WatermarkScope = WatermarkScope.ALL_PAGES
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("enabled", enabled)
        obj.put("type", type.name)
        obj.put("text", text)
        obj.put("imagePath", imagePath ?: "")
        obj.put("fontSizeSp", fontSizeSp.toDouble())
        obj.put("colorHex", colorHex)
        obj.put("opacity", opacity.toDouble())
        obj.put("rotationDegrees", rotationDegrees.toDouble())
        obj.put("layout", layout.name)
        obj.put("applyScope", applyScope.name)
        return obj.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): WatermarkConfig {
            if (jsonStr.isBlank()) return WatermarkConfig()
            return try {
                val obj = JSONObject(jsonStr)
                WatermarkConfig(
                    enabled = obj.optBoolean("enabled", false),
                    type = try { WatermarkType.valueOf(obj.optString("type", "TEXT")) } catch (_: Exception) { WatermarkType.TEXT },
                    text = obj.optString("text", "CONFIDENTIAL"),
                    imagePath = obj.optString("imagePath").takeIf { it.isNotBlank() },
                    fontSizeSp = obj.optDouble("fontSizeSp", 36.0).toFloat(),
                    colorHex = obj.optString("colorHex", "#888888"),
                    opacity = obj.optDouble("opacity", 0.35).toFloat(),
                    rotationDegrees = obj.optDouble("rotationDegrees", -45.0).toFloat(),
                    layout = try { WatermarkLayout.valueOf(obj.optString("layout", "TILED")) } catch (_: Exception) { WatermarkLayout.TILED },
                    applyScope = try { WatermarkScope.valueOf(obj.optString("applyScope", "ALL_PAGES")) } catch (_: Exception) { WatermarkScope.ALL_PAGES }
                )
            } catch (e: Exception) {
                WatermarkConfig()
            }
        }
    }
}
