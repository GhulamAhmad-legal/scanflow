package com.example.ocr

import android.graphics.Rect
import org.json.JSONArray
import org.json.JSONObject

data class OcrWordBox(
    val text: String,
    val left: Float,   // 0.0 to 1.0 (normalized)
    val top: Float,    // 0.0 to 1.0
    val right: Float,  // 0.0 to 1.0
    val bottom: Float  // 0.0 to 1.0
)

data class OcrLineBox(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val words: List<OcrWordBox>
)

data class OcrBlockBox(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val lines: List<OcrLineBox>
)

data class OcrDocumentResult(
    val fullText: String,
    val blocks: List<OcrBlockBox>,
    val script: String
) {
    fun toJson(): String {
        val root = JSONObject()
        root.put("fullText", fullText)
        root.put("script", script)
        val blocksArray = JSONArray()
        for (b in blocks) {
            val bObj = JSONObject()
            bObj.put("text", b.text)
            bObj.put("l", b.left.toDouble())
            bObj.put("t", b.top.toDouble())
            bObj.put("r", b.right.toDouble())
            bObj.put("b", b.bottom.toDouble())

            val linesArray = JSONArray()
            for (l in b.lines) {
                val lObj = JSONObject()
                lObj.put("text", l.text)
                lObj.put("l", l.left.toDouble())
                lObj.put("t", l.top.toDouble())
                lObj.put("r", l.right.toDouble())
                lObj.put("b", l.bottom.toDouble())

                val wordsArray = JSONArray()
                for (w in l.words) {
                    val wObj = JSONObject()
                    wObj.put("text", w.text)
                    wObj.put("l", w.left.toDouble())
                    wObj.put("t", w.top.toDouble())
                    wObj.put("r", w.right.toDouble())
                    wObj.put("b", w.bottom.toDouble())
                    wordsArray.put(wObj)
                }
                lObj.put("words", wordsArray)
                linesArray.put(lObj)
            }
            bObj.put("lines", linesArray)
            blocksArray.put(bObj)
        }
        root.put("blocks", blocksArray)
        return root.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): OcrDocumentResult {
            if (jsonStr.isBlank()) return OcrDocumentResult("", emptyList(), "Latin")
            return try {
                val root = JSONObject(jsonStr)
                val fullText = root.optString("fullText", "")
                val script = root.optString("script", "Latin")
                val blocksArray = root.optJSONArray("blocks") ?: JSONArray()
                val blocks = mutableListOf<OcrBlockBox>()

                for (i in 0 until blocksArray.length()) {
                    val bObj = blocksArray.getJSONObject(i)
                    val linesArray = bObj.optJSONArray("lines") ?: JSONArray()
                    val lines = mutableListOf<OcrLineBox>()

                    for (j in 0 until linesArray.length()) {
                        val lObj = linesArray.getJSONObject(j)
                        val wordsArray = lObj.optJSONArray("words") ?: JSONArray()
                        val words = mutableListOf<OcrWordBox>()

                        for (k in 0 until wordsArray.length()) {
                            val wObj = wordsArray.getJSONObject(k)
                            words.add(
                                OcrWordBox(
                                    text = wObj.optString("text", ""),
                                    left = wObj.optDouble("l", 0.0).toFloat(),
                                    top = wObj.optDouble("t", 0.0).toFloat(),
                                    right = wObj.optDouble("r", 0.0).toFloat(),
                                    bottom = wObj.optDouble("b", 0.0).toFloat()
                                )
                            )
                        }

                        lines.add(
                            OcrLineBox(
                                text = lObj.optString("text", ""),
                                left = lObj.optDouble("l", 0.0).toFloat(),
                                top = lObj.optDouble("t", 0.0).toFloat(),
                                right = lObj.optDouble("r", 0.0).toFloat(),
                                bottom = lObj.optDouble("b", 0.0).toFloat(),
                                words = words
                            )
                        )
                    }

                    blocks.add(
                        OcrBlockBox(
                            text = bObj.optString("text", ""),
                            left = bObj.optDouble("l", 0.0).toFloat(),
                            top = bObj.optDouble("t", 0.0).toFloat(),
                            right = bObj.optDouble("r", 0.0).toFloat(),
                            bottom = bObj.optDouble("b", 0.0).toFloat(),
                            lines = lines
                        )
                    )
                }

                OcrDocumentResult(fullText, blocks, script)
            } catch (e: Exception) {
                OcrDocumentResult("", emptyList(), "Latin")
            }
        }
    }
}
