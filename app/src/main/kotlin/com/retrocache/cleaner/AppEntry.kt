package com.retrocache.cleaner

import android.graphics.drawable.Drawable

/** Une application affichée dans la liste, avec ses tailles réelles quand elles sont lisibles. */
class AppEntry(
    val label: String,
    val packageName: String,
    val icon: Drawable?
) {
    var cacheBytes: Long = 0L
    var dataBytes: Long = 0L          // données utilisateur hors cache
    var hasStats: Boolean = false
}
