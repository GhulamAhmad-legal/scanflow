package com.example.signature

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class PlacedSignature(
    val id: String = UUID.randomUUID().toString(),
    val signatureId: Long,
    val path: String,
    val normalizedX: Float = 0.5f,
    val normalizedY: Float = 0.5f,
    val normalizedWidth: Float = 0.35f,
    val normalizedHeight: Float = 0.15f,
    val rotationDegrees: Float = 0f,
    val stampDate: String? = null,
    val stampText: String? = null
) {
    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("signatureId", signatureId)
        obj.put("path", path)
        obj.put("x", normalizedX.toDouble())
        obj.put("y", normalizedY.toDouble())
        obj.put("w", normalizedWidth.toDouble())
        obj.put("h", normalizedHeight.toDouble())
        obj.put("rotation", rotationDegrees.toDouble())
        obj.put("stampDate", stampDate ?: "")
        obj.put("stampText", stampText ?: "")
        return obj
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): PlacedSignature {
            return PlacedSignature(
                id = obj.optString("id", UUID.randomUUID().toString()),
                signatureId = obj.optLong("signatureId", 0L),
                path = obj.optString("path", ""),
                normalizedX = obj.optDouble("x", 0.5).toFloat(),
                normalizedY = obj.optDouble("y", 0.5).toFloat(),
                normalizedWidth = obj.optDouble("w", 0.35).toFloat(),
                normalizedHeight = obj.optDouble("h", 0.15).toFloat(),
                rotationDegrees = obj.optDouble("rotation", 0.0).toFloat(),
                stampDate = obj.optString("stampDate").takeIf { it.isNotBlank() },
                stampText = obj.optString("stampText").takeIf { it.isNotBlank() }
            )
        }

        fun listToJson(list: List<PlacedSignature>): String {
            val array = JSONArray()
            list.forEach { array.put(it.toJsonObject()) }
            return array.toString()
        }

        fun listFromJson(jsonStr: String): List<PlacedSignature> {
            if (jsonStr.isBlank()) return emptyList()
            return try {
                val array = JSONArray(jsonStr)
                val result = mutableListOf<PlacedSignature>()
                for (i in 0 until array.length()) {
                    result.add(fromJsonObject(array.getJSONObject(i)))
                }
                result
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
