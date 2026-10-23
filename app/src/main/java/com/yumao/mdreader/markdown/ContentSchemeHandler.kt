package com.yumao.mdreader.markdown

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import io.noties.markwon.image.ImageItem
import io.noties.markwon.image.SchemeHandler
import java.io.BufferedInputStream

/**
 * Loads content:// images (files shared from QQ etc.) via the ContentResolver
 * with bounds-based downsampling.
 */
class ContentSchemeHandler(private val context: Context) : SchemeHandler() {

    override fun handle(link: String, uri: Uri): ImageItem {
        return try {
            val resolver = context.contentResolver
            val probe = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BufferedInputStream(resolver.openInputStream(uri)).use {
                BitmapFactory.decodeStream(it, null, probe)
            }
            if (probe.outWidth <= 0 || probe.outHeight <= 0) return ImageItem.withResult(emptyColorDrawable())
            var sample = 1
            while (probe.outWidth / (sample * 2) >= 2048 || probe.outHeight / (sample * 2) >= 2048) sample *= 2
            val decode = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp: Bitmap? = BufferedInputStream(resolver.openInputStream(uri)).use {
                BitmapFactory.decodeStream(it, null, decode)
            }
            if (bmp != null) {
                ImageItem.withResult(BitmapDrawable(context.resources, bmp))
            } else {
                ImageItem.withResult(emptyColorDrawable())
            }
        } catch (t: Throwable) {
            ImageItem.withResult(emptyColorDrawable())
        }
    }

    override fun supportedSchemes(): Collection<String> = setOf("content")

    private fun emptyColorDrawable() = ColorDrawable(0x00000000)
}
