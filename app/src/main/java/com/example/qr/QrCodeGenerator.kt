package com.example.qr

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.min

/**
 * Lightweight, zero-dependency QR Code matrix generator.
 * Encodes text into a standard QR code 2D boolean grid.
 */
object QrCodeGenerator {

    /**
     * Creates a boolean matrix (true for black module, false for white module)
     * using standard QR specification byte encoding with Error Correction.
     */
    fun generateQrMatrix(content: String): Array<BooleanArray> {
        val version = getBestVersion(content.length)
        val size = 17 + 4 * version
        val modules = Array(size) { BooleanArray(size) { false } }
        val isFunction = Array(size) { BooleanArray(size) { false } }

        // 1. Finder patterns at 3 corners
        placeFinderPattern(modules, isFunction, 0, 0)
        placeFinderPattern(modules, isFunction, size - 7, 0)
        placeFinderPattern(modules, isFunction, 0, size - 7)

        // 2. Alignment patterns (version >= 2)
        if (version >= 2) {
            val alignPos = getAlignmentPositions(version)
            for (r in alignPos) {
                for (c in alignPos) {
                    if (isFunction[r][c]) continue
                    placeAlignmentPattern(modules, isFunction, r - 2, c - 2)
                }
            }
        }

        // 3. Timing patterns
        for (i in 8 until size - 8) {
            val v = (i % 2 == 0)
            if (!isFunction[6][i]) {
                modules[6][i] = v
                isFunction[6][i] = true
            }
            if (!isFunction[i][6]) {
                modules[i][6] = v
                isFunction[i][6] = true
            }
        }

        // 4. Dark module
        modules[4 * version + 9][8] = true
        isFunction[4 * version + 9][8] = true

        // 5. Reserve format info
        reserveFormatInfo(isFunction, size)

        // 6. Encode Data (Byte Mode 8-bit)
        val dataBits = encodeData(content, version)

        // 7. Place data bits in matrix with standard zigzag pattern & mask 0
        var bitIndex = 0
        var right = size - 1
        var goingUp = true

        while (right > 0) {
            if (right == 6) right-- // Skip vertical timing line
            val rows = if (goingUp) (size - 1 downTo 0) else (0 until size)
            for (y in rows) {
                for (xOffset in 0..1) {
                    val x = right - xOffset
                    if (!isFunction[y][x]) {
                        val bit = if (bitIndex < dataBits.size) dataBits[bitIndex++] else false
                        // Mask pattern 0: (row + col) % 2 == 0
                        val mask = (y + x) % 2 == 0
                        modules[y][x] = bit xor mask
                    }
                }
            }
            right -= 2
            goingUp = !goingUp
        }

        // 8. Add Format Information (ECC Level L, Mask 0 -> 0x77C4)
        applyFormatInfo(modules, size, 0x77C4)

        return modules
    }

    private fun getBestVersion(dataLength: Int): Int {
        return when {
            dataLength <= 14 -> 1
            dataLength <= 26 -> 2
            dataLength <= 42 -> 3
            dataLength <= 62 -> 4
            dataLength <= 84 -> 5
            dataLength <= 106 -> 6
            else -> 7
        }
    }

    private fun placeFinderPattern(modules: Array<BooleanArray>, isFunc: Array<BooleanArray>, r: Int, c: Int) {
        for (y in 0..6) {
            for (x in 0..6) {
                val isBlack = (y == 0 || y == 6 || x == 0 || x == 6 || (y in 2..4 && x in 2..4))
                modules[r + y][c + x] = isBlack
                isFunc[r + y][c + x] = true
            }
        }
        // Separators
        for (y in -1..7) {
            for (x in -1..7) {
                val row = r + y
                val col = c + x
                if (row in modules.indices && col in modules.indices && !isFunc[row][col]) {
                    modules[row][col] = false
                    isFunc[row][col] = true
                }
            }
        }
    }

    private fun placeAlignmentPattern(modules: Array<BooleanArray>, isFunc: Array<BooleanArray>, r: Int, c: Int) {
        for (y in 0..4) {
            for (x in 0..4) {
                val isBlack = (y == 0 || y == 4 || x == 0 || x == 4 || (y == 2 && x == 2))
                modules[r + y][c + x] = isBlack
                isFunc[r + y][c + x] = true
            }
        }
    }

    private fun getAlignmentPositions(version: Int): IntArray {
        return when (version) {
            2 -> intArrayOf(6, 18)
            3 -> intArrayOf(6, 22)
            4 -> intArrayOf(6, 26)
            5 -> intArrayOf(6, 30)
            6 -> intArrayOf(6, 34)
            7 -> intArrayOf(6, 22, 38)
            else -> intArrayOf(6, 18)
        }
    }

    private fun reserveFormatInfo(isFunc: Array<BooleanArray>, size: Int) {
        for (i in 0..8) {
            isFunc[8][i] = true
            isFunc[i][8] = true
        }
        for (i in 0..7) {
            isFunc[8][size - 1 - i] = true
            isFunc[size - 1 - i][8] = true
        }
    }

    private fun applyFormatInfo(modules: Array<BooleanArray>, size: Int, formatBits: Int) {
        for (i in 0..14) {
            val bit = ((formatBits shr (14 - i)) and 1) == 1
            // Top-left
            when {
                i <= 5 -> modules[8][i] = bit
                i == 6 -> modules[8][7] = bit
                i == 7 -> modules[8][8] = bit
                i == 8 -> modules[7][8] = bit
                else -> modules[14 - i][8] = bit
            }
            // Split around corners
            if (i < 8) {
                modules[size - 1 - i][8] = bit
            } else {
                modules[8][size - 15 + i] = bit
            }
        }
    }

    private fun encodeData(content: String, version: Int): BooleanArray {
        val bytes = content.toByteArray(Charsets.ISO_8859_1)
        val bitList = ArrayList<Boolean>()

        // 1. Mode indicator (0100 for Byte Mode)
        addBits(bitList, 4, 4)

        // 2. Character count indicator (8 bits for versions 1-9)
        addBits(bitList, bytes.size, 8)

        // 3. Data bytes
        for (b in bytes) {
            addBits(bitList, b.toInt() and 0xFF, 8)
        }

        // 4. Terminator (up to 4 zeroes)
        val capacityBytes = getTotalDataCodewords(version)
        val capacityBits = capacityBytes * 8
        val terminatorLen = min(4, capacityBits - bitList.size)
        addBits(bitList, 0, terminatorLen)

        // 5. Pad to multiple of 8
        while (bitList.size % 8 != 0) {
            bitList.add(false)
        }

        // 6. Pad codewords (0xEC, 0x11)
        var padToggle = true
        while (bitList.size < capacityBits) {
            addBits(bitList, if (padToggle) 0xEC else 0x11, 8)
            padToggle = !padToggle
        }

        // 7. Calculate Reed-Solomon Error Correction Code
        val dataCodewords = IntArray(capacityBytes)
        for (i in 0 until capacityBytes) {
            var byteVal = 0
            for (b in 0..7) {
                if (bitList[i * 8 + b]) {
                    byteVal = byteVal or (1 shl (7 - b))
                }
            }
            dataCodewords[i] = byteVal
        }

        val ecCodewordsCount = getEcCodewordsCount(version)
        val ecCodewords = calculateReedSolomon(dataCodewords, ecCodewordsCount)

        // Combine Data + EC
        val allBits = ArrayList<Boolean>()
        for (v in dataCodewords) {
            addBits(allBits, v, 8)
        }
        for (v in ecCodewords) {
            addBits(allBits, v, 8)
        }

        return allBits.toBooleanArray()
    }

    private fun getTotalDataCodewords(version: Int): Int {
        return when (version) {
            1 -> 19
            2 -> 34
            3 -> 55
            4 -> 80
            5 -> 108
            6 -> 136
            7 -> 156
            else -> 19
        }
    }

    private fun getEcCodewordsCount(version: Int): Int {
        return when (version) {
            1 -> 7
            2 -> 10
            3 -> 15
            4 -> 20
            5 -> 26
            6 -> 36
            7 -> 40
            else -> 7
        }
    }

    private fun calculateReedSolomon(data: IntArray, ecCount: Int): IntArray {
        // GF(256) primitive polynomial 0x11D (285)
        val exp = IntArray(512)
        val log = IntArray(256)
        var x = 1
        for (i in 0 until 255) {
            exp[i] = x
            exp[i + 255] = x
            log[x] = i
            x = (x shl 1)
            if (x >= 256) x = x xor 285
        }

        // Generator polynomial for ecCount
        var gen = intArrayOf(1)
        for (i in 0 until ecCount) {
            val root = exp[i]
            val nextGen = IntArray(gen.size + 1)
            for (j in gen.indices) {
                nextGen[j] = nextGen[j] xor gen[j]
                val prod = if (gen[j] == 0) 0 else exp[(log[gen[j]] + log[root]) % 255]
                nextGen[j + 1] = nextGen[j + 1] xor prod
            }
            gen = nextGen
        }

        // Polynomial division
        val result = IntArray(ecCount)
        val msg = IntArray(data.size + ecCount)
        System.arraycopy(data, 0, msg, 0, data.size)

        for (i in data.indices) {
            val coef = msg[i]
            if (coef != 0) {
                val logCoef = log[coef]
                for (j in gen.indices) {
                    if (gen[j] != 0) {
                        msg[i + j] = msg[i + j] xor exp[(log[gen[j]] + logCoef) % 255]
                    }
                }
            }
        }

        System.arraycopy(msg, data.size, result, 0, ecCount)
        return result
    }

    private fun addBits(list: ArrayList<Boolean>, value: Int, length: Int) {
        for (i in length - 1 downTo 0) {
            list.add(((value shr i) and 1) == 1)
        }
    }

    /**
     * Converts a boolean matrix to an Android Bitmap
     */
    fun createQrBitmap(content: String, targetSizePx: Int = 400): Bitmap {
        val matrix = generateQrMatrix(content)
        val matrixSize = matrix.size
        val border = 2
        val totalSize = matrixSize + border * 2
        val scale = targetSizePx / totalSize

        val bitmap = Bitmap.createBitmap(totalSize * scale, totalSize * scale, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = android.graphics.Paint().apply {
            color = Color.BLACK
            style = android.graphics.Paint.Style.FILL
        }

        for (r in matrix.indices) {
            for (c in matrix[r].indices) {
                if (matrix[r][c]) {
                    val left = (c + border) * scale
                    val top = (r + border) * scale
                    canvas.drawRect(
                        left.toFloat(),
                        top.toFloat(),
                        (left + scale).toFloat(),
                        (top + scale).toFloat(),
                        paint
                    )
                }
            }
        }

        return bitmap
    }
}
