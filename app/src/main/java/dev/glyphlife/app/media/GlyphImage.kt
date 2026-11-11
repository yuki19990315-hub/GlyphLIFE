package dev.glyphlife.app.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import dev.glyphlife.app.model.GlyphAnimation
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import dev.glyphlife.app.model.GlyphMask
import dev.glyphlife.app.model.PixelArt

/** Locally converts an image to a 13x13 monochrome Glyph sprite. No AI or upload. */
object GlyphImage {
    fun importFromUri(context: Context, uri: Uri): List<Boolean> {
        val resolver = context.contentResolver
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        require(options.outWidth in 1..20_000 && options.outHeight in 1..20_000)
        var sample = 1
        while (options.outWidth / sample > 256 || options.outHeight / sample > 256) sample *= 2
        options.inJustDecodeBounds = false
        options.inSampleSize = sample
        val original = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error("画像を読み込めません")
        val scaled = Bitmap.createScaledBitmap(original, 13, 13, true)
        if (scaled !== original) original.recycle()
        try {
            val values = FloatArray(PixelArt.COUNT) { i ->
                val color = scaled.getPixel(i % 13, i / 13)
                (Color.red(color) * .299f + Color.green(color) * .587f +
                    Color.blue(color) * .114f) * (Color.alpha(color) / 255f)
            }
            // Floyd–Steinberg dithering; use 1-bit grayscale for reliable hardware results.
            val lit = BooleanArray(PixelArt.COUNT)
            for (y in 0..12) for (x in 0..12) {
                val i = y * 13 + x
                val after = if (values[i] >= 128) 255f else 0f
                lit[i] = after > 0
                val err = values[i] - after
                fun spread(nx: Int, ny: Int, weight: Float) {
                    if (nx in 0..12 && ny in 0..12) values[ny * 13 + nx] += err * weight
                }
                spread(x + 1, y, 7f / 16)
                spread(x - 1, y + 1, 3f / 16)
                spread(x, y + 1, 5f / 16)
                spread(x + 1, y + 1, 1f / 16)
            }
            return GlyphMask.normalize(lit.toList())
        } finally {
            if (!scaled.isRecycled) scaled.recycle()
        }
    }

    fun savePng(context: Context, pixels: List<Boolean>): Uri {
        require(pixels.size == PixelArt.COUNT)
        val bitmap = Bitmap.createBitmap(13, 13, Bitmap.Config.ARGB_8888)
        pixels.forEachIndexed { i, on ->
            bitmap.setPixel(i % 13, i / 13, if (on) Color.WHITE else Color.BLACK)
        }
        val enlarged = Bitmap.createScaledBitmap(bitmap, 520, 520, false)
        bitmap.recycle()
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "glyph-life-${System.currentTimeMillis()}.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/GlyphLife")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("画像保存先を作れません")
        try {
            resolver.openOutputStream(target)?.use { stream ->
                check(enlarged.compress(Bitmap.CompressFormat.PNG, 100, stream))
            } ?: error("画像の書き込みに失敗しました")
            resolver.update(target, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
            return target
        } catch (e: Exception) {
            resolver.delete(target, null, null)
            throw e
        } finally {
            enlarged.recycle()
        }
    }

    /** Export ALL animation frames as a compact 8-column PNG sprite sheet. */
    fun saveSpriteSheet(context: Context, animation: GlyphAnimation): Uri {
        val scale = 24
        val cellSize = 13 * scale
        val cols = minOf(8, animation.frames.size)
        val rows = (animation.frames.size + cols - 1) / cols
        val sheet = Bitmap.createBitmap(
            cellSize * cols, cellSize * rows, Bitmap.Config.ARGB_8888
        )
        try {
            val canvas = Canvas(sheet)
            canvas.drawColor(Color.BLACK)
            val white = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
                isAntiAlias = false
            }
            animation.frames.forEachIndexed { frameIndex, frame ->
                val ox = (frameIndex % cols) * cellSize
                val oy = (frameIndex / cols) * cellSize
                frame.pixels.forEachIndexed { index, lit ->
                    if (lit && GlyphMask.isPhysical(index)) {
                        val x = ox + (index % 13) * scale
                        val y = oy + (index / 13) * scale
                        canvas.drawRect(x.toFloat(), y.toFloat(),
                            (x + scale).toFloat(), (y + scale).toFloat(), white)
                    }
                }
            }
            return saveToGallery(context, sheet, "glyph-life-spritesheet")
        } finally {
            sheet.recycle()
        }
    }

    private fun saveToGallery(context: Context, bitmap: Bitmap, prefix: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$prefix-${System.currentTimeMillis()}.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/GlyphLife")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("画像保存先を作れません")
        try {
            resolver.openOutputStream(target)?.use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            } ?: error("画像の書き込みに失敗しました")
            resolver.update(target, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
            return target
        } catch (e: Exception) {
            resolver.delete(target, null, null)
            throw e
        }
    }
}
