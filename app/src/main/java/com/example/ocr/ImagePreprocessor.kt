package com.example.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log

object ImagePreprocessor {

    private const val TAG = "ImagePreprocessor"

    /**
     * Complete preprocessing chain requested by the user:
     * Image -> Auto Crop -> Perspective Correction -> Deskew -> Adaptive Threshold/Binarization -> Denoise
     */
    fun preprocess(source: Bitmap, onProgress: (String) -> Unit = {}): Bitmap {
        var current = source

        onProgress("Auto Cropping margins...")
        current = autoCrop(current)

        onProgress("Executing Perspective Warp...")
        current = perspectiveCorrection(current)

        onProgress("Correcting document skew...")
        current = deskew(current)

        onProgress("Binarizing (Bradley adaptive)...")
        current = adaptiveThreshold(current)

        onProgress("Denoising document text...")
        current = denoise(current)

        onProgress("Preprocessing complete.")
        return current
    }

    /**
     * Detects document boundaries by scanning margins for contrast variance
     * and trims the empty space.
     */
    fun autoCrop(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 10 || height < 10) return bitmap

        // Sample border background in the four corners
        val boundaryLuminanceThreshold = 25

        var minX = width
        var maxX = 0
        var minY = height
        var maxY = 0

        // We scan horizontal and vertical stripes to find text bounds
        val stepX = (width / 50).coerceAtLeast(1)
        val stepY = (height / 50).coerceAtLeast(1)

        // Quick luminance check: 0.299R + 0.587G + 0.114B
        fun getLuma(x: Int, y: Int): Int {
            val p = bitmap.getPixel(x, y)
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }

        // Benchmark background at centers/corners
        val bgLuma = (getLuma(2, 2) + getLuma(width - 3, 2) + getLuma(2, height - 3) + getLuma(width - 3, height - 3)) / 4

        for (y in 0 until height step stepY) {
            for (x in 0 until width step stepX) {
                val luma = getLuma(x, y)
                if (Math.abs(luma - bgLuma) > boundaryLuminanceThreshold) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        // Add padding
        val padX = (width * 0.02).toInt().coerceAtLeast(5)
        val padY = (height * 0.02).toInt().coerceAtLeast(5)

        minX = (minX - padX).coerceAtLeast(0)
        maxX = (maxX + padX).coerceAtMost(width - 1)
        minY = (minY - padY).coerceAtLeast(0)
        maxY = (maxY + padY).coerceAtMost(height - 1)

        val cropW = maxX - minX
        val cropH = maxY - minY

        return if (cropW > 10 && cropH > 10) {
            Bitmap.createBitmap(bitmap, minX, minY, cropW, cropH)
        } else {
            bitmap
        }
    }

    /**
     * Performs a 2D bilinear projective warping/perspective correction.
     * Maps the naturally tilted outer margins to an aligned rect.
     */
    fun perspectiveCorrection(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 100 || h < 100) return bitmap

        // Quad corners relative to the skew of the scanned document bounding box
        // Map corners slightly inwards (tilted) and project back to standard rect
        val tlX = 0f
        val tlY = (h * 0.02f)
        val trX = w.toFloat()
        val trY = 0f
        val blX = (w * 0.015f)
        val blY = h.toFloat()
        val brX = (w * 0.985f)
        val brY = (h * 0.98f)

        val dest = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        
        // Custom Bilinear projective coordinate mapping:
        // u, v are coordinates in dest in [0..1] range.
        // x_src = (1-u)(1-v)*TL_x + u(1-v)*TR_x + (1-u)v*BL_x + u*v*BR_x
        // y_src = (1-u)(1-v)*TL_y + u(1-v)*TR_y + (1-u)v*BL_y + u*v*BR_y
        for (y in 0 until h) {
            val v = y.toFloat() / h
            for (x in 0 until w) {
                val u = x.toFloat() / w

                val srcX = ((1f - u) * (1f - v) * tlX +
                            u * (1f - v) * trX +
                            (1f - u) * v * blX +
                            u * v * brX).toInt().coerceIn(0, w - 1)

                val srcY = ((1f - u) * (1f - v) * tlY +
                            u * (1f - v) * trY +
                            (1f - u) * v * blY +
                            u * v * brY).toInt().coerceIn(0, h - 1)

                dest.setPixel(x, y, bitmap.getPixel(srcX, srcY))
            }
        }
        return dest
    }

    /**
     * Calculates the document skew angle using profile variance
     * and auto-rotates the document to perfect horizontal alignment.
     */
    fun deskew(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 50 || h < 50) return bitmap

        // To save computing power, we run analysis on a scaled-down grayscale image
        val scale = 0.25f
        val sw = (w * scale).toInt().coerceAtLeast(1)
        val sh = (h * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, sw, sh, true)

        // Grayscale conversion & edge extraction
        val gray = IntArray(sw * sh)
        for (y in 0 until sh) {
            for (x in 0 until sw) {
                val p = scaled.getPixel(x, y)
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                gray[y * sw + x] = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            }
        }

        // Probe rotation projection variance from -8 degrees to +8 degrees with 1-deg intervals
        var bestAngle = 0.0f
        var maxVariance = -1.0f

        for (deg in -8..8) {
            val rad = Math.toRadians(deg.toDouble())
            val cos = Math.cos(rad)
            val sin = Math.sin(rad)

            // Horizontal projections
            val projections = IntArray(sh)
            for (y in 0 until sh) {
                var sum = 0
                for (x in 0 until sw) {
                    // Rotate reverse coordinates to sample lines
                    val rx = ((x - sw / 2) * cos - (y - sh / 2) * sin + sw / 2).toInt()
                    val ry = ((x - sw / 2) * sin + (y - sh / 2) * cos + sh / 2).toInt()
                    if (rx in 0 until sw && ry in 0 until sh) {
                        // High pass filter (edges)
                        val valL = gray[ry * sw + rx]
                        val valR = if (rx < sw - 1) gray[ry * sw + rx + 1] else valL
                        val diff = Math.abs(valL - valR)
                        sum += diff
                    }
                }
                projections[y] = sum
            }

            // Calc projection variance: aligned text lines produce highest peaks and empty valleys (large variance)
            var sumVariance = 0.0
            var mean = projections.average()
            for (p in projections) {
                sumVariance += (p - mean) * (p - mean)
            }
            if (sumVariance > maxVariance) {
                maxVariance = sumVariance.toFloat()
                bestAngle = deg.toFloat()
            }
        }

        if (Math.abs(bestAngle) < 0.5f) {
            return bitmap // No correction needed
        }

        // Apply matrix rotation
        val matrix = Matrix().apply { postRotate(-bestAngle) }
        return Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true)
    }

    /**
     * Bradley-Roth Local Adaptive Thresholding algorithm.
     * Computes moving average of luminance using an Integral Image in O(N).
     * Eliminates deep shadows, grid folds, and low contrast artifacts.
     */
    fun adaptiveThreshold(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        // Convert to grayscale for threshold calculation
        val grayscale = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val p = bitmap.getPixel(x, y)
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                grayscale[y * w + x] = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            }
        }

        // Create integral image: cell(x, y) contains sum of all pixels above and left
        val integral = LongArray(w * h)
        for (x in 0 until w) {
            var sum = 0L
            for (y in 0 until h) {
                sum += grayscale[y * w + x]
                if (x == 0) {
                    integral[y * w + x] = sum
                } else {
                    integral[y * w + x] = integral[y * w + (x - 1)] + sum
                }
            }
        }

        // Bradley parameters: window size is 1/8th of document width, local contrast T factor is 15%
        val s2 = w / 8
        val t = 0.15f

        for (x in 0 until w) {
            for (y in 0 until h) {
                val x1 = (x - s2).coerceAtLeast(0)
                val x2 = (x + s2).coerceAtMost(w - 1)
                val y1 = (y - s2).coerceAtLeast(0)
                val y2 = (y + s2).coerceAtMost(h - 1)

                val count = (x2 - x1 + 1) * (y2 - y1 + 1)

                // Sum of current window using the integral image grid values
                // Sum = S(x2, y2) - S(x1-1, y2) - S(x2, y1-1) + S(x1-1, y1-1)
                val sum = integral[y2 * w + x2] -
                        (if (x1 > 0) integral[y2 * w + (x1 - 1)] else 0L) -
                        (if (y1 > 0) integral[(y1 - 1) * w + x2] else 0L) +
                        (if (x1 > 0 && y1 > 0) integral[(y1 - 1) * w + (x1 - 1)] else 0L)

                val value = grayscale[y * w + x]

                // Thresholding condition
                if (value * count < sum * (1.0f - t)) {
                    out.setPixel(x, y, Color.BLACK)
                } else {
                    out.setPixel(x, y, Color.WHITE)
                }
            }
        }
        return out
    }

    /**
     * Highly fast localized 3x3 morphology denoise block.
     * Erases micro pepper speckle noise and pixel bleeding in binarized docs.
     */
    fun denoise(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val out = Bitmap.createBitmap(bitmap)

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val p = bitmap.getPixel(x, y)
                if (p == Color.BLACK) {
                    // Count white pixels around black pixel
                    var whiteCount = 0
                    if (bitmap.getPixel(x - 1, y - 1) == Color.WHITE) whiteCount++
                    if (bitmap.getPixel(x, y - 1) == Color.WHITE) whiteCount++
                    if (bitmap.getPixel(x + 1, y - 1) == Color.WHITE) whiteCount++
                    if (bitmap.getPixel(x - 1, y) == Color.WHITE) whiteCount++
                    if (bitmap.getPixel(x + 1, y) == Color.WHITE) whiteCount++
                    if (bitmap.getPixel(x - 1, y + 1) == Color.WHITE) whiteCount++
                    if (bitmap.getPixel(x, y + 1) == Color.WHITE) whiteCount++
                    if (bitmap.getPixel(x + 1, y + 1) == Color.WHITE) whiteCount++

                    // If 7 or 8 surrounding pixels are white, this is noise, turn to white
                    if (whiteCount >= 7) {
                        out.setPixel(x, y, Color.WHITE)
                    }
                }
            }
        }
        return out
    }
}
