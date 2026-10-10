package com.example.annotate

import android.graphics.PointF
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class AnnotationType {
    PEN,
    HIGHLIGHTER,
    RECTANGLE,
    ELLIPSE,
    LINE,
    ARROW,
    TEXT
}

sealed class AnnotationItem(
    open val id: String = UUID.randomUUID().toString(),
    open val type: AnnotationType
) {
    abstract fun toJsonObject(): JSONObject

    data class FreehandStroke(
        override val id: String = UUID.randomUUID().toString(),
        override val type: AnnotationType = AnnotationType.PEN, // PEN or HIGHLIGHTER
        val points: List<PointF>, // normalized 0..1 coordinates
        val colorHex: String = "#000000",
        val strokeWidth: Float = 3f,
        val isHighlighter: Boolean = false
    ) : AnnotationItem(id, type) {
        override fun toJsonObject(): JSONObject {
            val obj = JSONObject()
            obj.put("id", id)
            obj.put("type", if (isHighlighter) "HIGHLIGHTER" else "PEN")
            obj.put("colorHex", colorHex)
            obj.put("strokeWidth", strokeWidth.toDouble())
            obj.put("isHighlighter", isHighlighter)
            val ptsArray = JSONArray()
            points.forEach { pt ->
                val pObj = JSONObject()
                pObj.put("x", pt.x.toDouble())
                pObj.put("y", pt.y.toDouble())
                ptsArray.put(pObj)
            }
            obj.put("points", ptsArray)
            return obj
        }
    }

    data class ShapeItem(
        override val id: String = UUID.randomUUID().toString(),
        override val type: AnnotationType, // RECTANGLE, ELLIPSE, LINE, ARROW
        val start: PointF, // normalized
        val end: PointF,   // normalized
        val colorHex: String = "#EF4444",
        val strokeWidth: Float = 3f,
        val isFilled: Boolean = false
    ) : AnnotationItem(id, type) {
        override fun toJsonObject(): JSONObject {
            val obj = JSONObject()
            obj.put("id", id)
            obj.put("type", type.name)
            obj.put("colorHex", colorHex)
            obj.put("strokeWidth", strokeWidth.toDouble())
            obj.put("isFilled", isFilled)
            obj.put("sx", start.x.toDouble())
            obj.put("sy", start.y.toDouble())
            obj.put("ex", end.x.toDouble())
            obj.put("ey", end.y.toDouble())
            return obj
        }
    }

    data class TextItem(
        override val id: String = UUID.randomUUID().toString(),
        override val type: AnnotationType = AnnotationType.TEXT,
        val text: String,
        val position: PointF, // normalized
        val colorHex: String = "#000000",
        val fontSize: Float = 16f,
        val isBold: Boolean = false
    ) : AnnotationItem(id, type) {
        override fun toJsonObject(): JSONObject {
            val obj = JSONObject()
            obj.put("id", id)
            obj.put("type", "TEXT")
            obj.put("text", text)
            obj.put("x", position.x.toDouble())
            obj.put("y", position.y.toDouble())
            obj.put("colorHex", colorHex)
            obj.put("fontSize", fontSize.toDouble())
            obj.put("isBold", isBold)
            return obj
        }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): AnnotationItem? {
            val id = obj.optString("id", UUID.randomUUID().toString())
            val typeStr = obj.optString("type", "PEN")
            return try {
                when (typeStr) {
                    "PEN", "HIGHLIGHTER" -> {
                        val isHighlighter = obj.optBoolean("isHighlighter", typeStr == "HIGHLIGHTER")
                        val ptsArray = obj.optJSONArray("points") ?: JSONArray()
                        val points = mutableListOf<PointF>()
                        for (i in 0 until ptsArray.length()) {
                            val p = ptsArray.getJSONObject(i)
                            points.add(PointF(p.optDouble("x", 0.0).toFloat(), p.optDouble("y", 0.0).toFloat()))
                        }
                        FreehandStroke(
                            id = id,
                            type = if (isHighlighter) AnnotationType.HIGHLIGHTER else AnnotationType.PEN,
                            points = points,
                            colorHex = obj.optString("colorHex", if (isHighlighter) "#FEF08A" else "#000000"),
                            strokeWidth = obj.optDouble("strokeWidth", if (isHighlighter) 18.0 else 3.0).toFloat(),
                            isHighlighter = isHighlighter
                        )
                    }
                    "RECTANGLE", "ELLIPSE", "LINE", "ARROW" -> {
                        val shapeType = AnnotationType.valueOf(typeStr)
                        ShapeItem(
                            id = id,
                            type = shapeType,
                            start = PointF(obj.optDouble("sx", 0.0).toFloat(), obj.optDouble("sy", 0.0).toFloat()),
                            end = PointF(obj.optDouble("ex", 0.0).toFloat(), obj.optDouble("ey", 0.0).toFloat()),
                            colorHex = obj.optString("colorHex", "#EF4444"),
                            strokeWidth = obj.optDouble("strokeWidth", 3.0).toFloat(),
                            isFilled = obj.optBoolean("isFilled", false)
                        )
                    }
                    "TEXT" -> {
                        TextItem(
                            id = id,
                            text = obj.optString("text", ""),
                            position = PointF(obj.optDouble("x", 0.0).toFloat(), obj.optDouble("y", 0.0).toFloat()),
                            colorHex = obj.optString("colorHex", "#000000"),
                            fontSize = obj.optDouble("fontSize", 16.0).toFloat(),
                            isBold = obj.optBoolean("isBold", false)
                        )
                    }
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }

        fun listToJson(list: List<AnnotationItem>): String {
            val array = JSONArray()
            list.forEach { array.put(it.toJsonObject()) }
            return array.toString()
        }

        fun listFromJson(jsonStr: String): List<AnnotationItem> {
            if (jsonStr.isBlank()) return emptyList()
            return try {
                val array = JSONArray(jsonStr)
                val result = mutableListOf<AnnotationItem>()
                for (i in 0 until array.length()) {
                    val item = fromJsonObject(array.getJSONObject(i))
                    if (item != null) result.add(item)
                }
                result
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
