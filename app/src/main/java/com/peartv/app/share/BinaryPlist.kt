package com.peartv.app.share

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal, zero-dependency Apple Binary Property List (bplist00) encoder and decoder.
 * Tailored specifically for AirDrop's /Discover and /Ask endpoints.
 */
object BinaryPlist {

    /**
     * Creates a standard AirDrop binary plist dictionary containing key-value pairs.
     * Supports String and ByteArray values.
     */
    fun createDict(vararg pairs: Pair<String, Any>): ByteArray {
        val out = ByteArrayOutputStream()
        // Header
        out.write("bplist00".toByteArray(Charsets.US_ASCII))

        val offsets = mutableListOf<Int>()
        val keyIndices = mutableListOf<Int>()
        val valIndices = mutableListOf<Int>()

        var objectIndex = 0

        // Root dictionary will be object 0
        val rootDictOffset = out.size()
        offsets.add(rootDictOffset)
        objectIndex++

        // Write placeholder for root dictionary header
        // We will finalize the root dict after writing keys and values
        // For simplicity, we write keys, then values, then root dict.
        val keys = pairs.map { it.first }
        val values = pairs.map { it.second }

        val objectsOut = ByteArrayOutputStream()
        val objOffsets = mutableListOf<Int>()

        fun writeString(str: String): Int {
            val idx = objOffsets.size
            objOffsets.add(objectsOut.size())
            val bytes = str.toByteArray(Charsets.UTF_8)
            if (bytes.size < 15) {
                objectsOut.write(0x50 or bytes.size)
            } else {
                objectsOut.write(0x5F)
                objectsOut.write(0x10) // 1 byte length
                objectsOut.write(bytes.size)
            }
            objectsOut.write(bytes)
            return idx
        }

        fun writeData(data: ByteArray): Int {
            val idx = objOffsets.size
            objOffsets.add(objectsOut.size())
            if (data.size < 15) {
                objectsOut.write(0x40 or data.size)
            } else {
                objectsOut.write(0x4F)
                objectsOut.write(0x10)
                objectsOut.write(data.size)
            }
            objectsOut.write(data)
            return idx
        }

        // Object 0: Dictionary header & references
        // Dictionary object index is 0
        objOffsets.add(0) // placeholder for dict at offset 0
        val dictHeaderOut = ByteArrayOutputStream()
        val count = pairs.size
        if (count < 15) {
            dictHeaderOut.write(0xD0 or count)
        } else {
            dictHeaderOut.write(0xDF)
            dictHeaderOut.write(0x10)
            dictHeaderOut.write(count)
        }

        // Now write all keys
        for (k in keys) {
            keyIndices.add(writeString(k))
        }

        // Now write all values
        for (v in values) {
            val idx = when (v) {
                is String -> writeString(v)
                is ByteArray -> writeData(v)
                else -> writeString(v.toString())
            }
            valIndices.add(idx)
        }

        // In binary plist dictionaries:
        // object reference array for keys, followed by object reference array for values
        for (kIdx in keyIndices) {
            dictHeaderOut.write(kIdx)
        }
        for (vIdx in valIndices) {
            dictHeaderOut.write(vIdx)
        }

        val dictBytes = dictHeaderOut.toByteArray()
        val remainingObjBytes = objectsOut.toByteArray()

        // Fix up offsets: dict is at offset 0 of object data
        // Other objects are shifted by dictBytes.size
        val finalObjOffsets = mutableListOf<Int>()
        finalObjOffsets.add(8) // header is 8 bytes
        for (i in 1 until objOffsets.size) {
            finalObjOffsets.add(8 + dictBytes.size + objOffsets[i])
        }

        out.write(dictBytes)
        out.write(remainingObjBytes)

        // Offset table
        val offsetTableStart = out.size()
        for (off in finalObjOffsets) {
            out.write(off)
        }

        // Trailer (32 bytes)
        // 6 unused bytes
        out.write(ByteArray(6))
        // 1 byte: offset_int_size (1 byte per offset)
        out.write(1)
        // 1 byte: object_ref_size (1 byte per object reference)
        out.write(1)
        // 8 bytes: num_objects
        val numBuf = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(finalObjOffsets.size.toLong())
        out.write(numBuf.array())
        // 8 bytes: top_object (0)
        out.write(ByteArray(8))
        // 8 bytes: offset_table_offset
        val offBuf = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(offsetTableStart.toLong())
        out.write(offBuf.array())

        return out.toByteArray()
    }

    /**
     * Extracts strings from an incoming AirDrop binary or XML plist.
     * Looks for keys like "SenderComputerName", "FileName", "Files".
     */
    fun extractStrings(data: ByteArray): Map<String, String> {
        if (data.size < 32) return emptyMap()
        try {
            if (data.take(8).toByteArray().toString(Charsets.US_ASCII) != "bplist00") {
                val text = String(data, Charsets.UTF_8)
                val map = mutableMapOf<String, String>()
                for (part in text.split('&')) {
                    val kv = part.split('=')
                    if (kv.size == 2) map[kv[0].trim()] = kv[1].trim()
                }
                return map
            }

            val trailer = data.copyOfRange(data.size - 32, data.size)
            val offsetIntSize = trailer[6].toInt() and 0xFF
            val objectRefSize = trailer[7].toInt() and 0xFF
            val numObjects = ByteBuffer.wrap(trailer, 8, 8).order(ByteOrder.BIG_ENDIAN).long.toInt()
            val offsetTableOffset = ByteBuffer.wrap(trailer, 24, 8).order(ByteOrder.BIG_ENDIAN).long.toInt()

            val offsets = IntArray(numObjects)
            for (i in 0 until numObjects) {
                var off = 0
                val start = offsetTableOffset + i * offsetIntSize
                for (b in 0 until offsetIntSize) {
                    off = (off shl 8) or (data[start + b].toInt() and 0xFF)
                }
                offsets[i] = off
            }

            fun readString(off: Int): String? {
                if (off >= data.size) return null
                val tag = data[off].toInt() and 0xFF
                val typeNibble = tag and 0xF0
                var length = tag and 0x0F
                var p = off + 1
                if (length == 15) {
                    val intTag = data[p].toInt() and 0xFF
                    val intSize = 1 shl (intTag and 0x0F)
                    var intVal = 0
                    for (b in 0 until intSize) {
                        intVal = (intVal shl 8) or (data[p + 1 + b].toInt() and 0xFF)
                    }
                    length = intVal
                    p += 1 + intSize
                }
                return when (typeNibble) {
                    0x50 -> {
                        if (p + length <= data.size) {
                            String(data, p, length, Charsets.UTF_8)
                        } else null
                    }
                    0x60 -> {
                        val byteLen = length * 2
                        if (p + byteLen <= data.size) {
                            String(data, p, byteLen, Charsets.UTF_16BE)
                        } else null
                    }
                    else -> null
                }
            }

            val rootOff = offsets[0]
            val rootTag = data[rootOff].toInt() and 0xFF
            if ((rootTag and 0xF0) != 0xD0) return emptyMap()

            var count = rootTag and 0x0F
            var p = rootOff + 1
            if (count == 15) {
                val intTag = data[p].toInt() and 0xFF
                val intSize = 1 shl (intTag and 0x0F)
                var intVal = 0
                for (b in 0 until intSize) {
                    intVal = (intVal shl 8) or (data[p + 1 + b].toInt() and 0xFF)
                }
                count = intVal
                p += 1 + intSize
            }

            val keyRefs = IntArray(count)
            for (i in 0 until count) {
                var ref = 0
                for (b in 0 until objectRefSize) {
                    ref = (ref shl 8) or (data[p + b].toInt() and 0xFF)
                }
                keyRefs[i] = ref
                p += objectRefSize
            }

            val valRefs = IntArray(count)
            for (i in 0 until count) {
                var ref = 0
                for (b in 0 until objectRefSize) {
                    ref = (ref shl 8) or (data[p + b].toInt() and 0xFF)
                }
                valRefs[i] = ref
                p += objectRefSize
            }

            val res = mutableMapOf<String, String>()
            for (i in 0 until count) {
                val kRef = keyRefs[i]
                val vRef = valRefs[i]
                if (kRef < offsets.size && vRef < offsets.size) {
                    val kStr = readString(offsets[kRef])
                    val vStr = readString(offsets[vRef])
                    if (kStr != null && vStr != null) {
                        res[kStr] = vStr
                    }
                }
            }
            return res
        } catch (_: Exception) {
            return emptyMap()
        }
    }
}
