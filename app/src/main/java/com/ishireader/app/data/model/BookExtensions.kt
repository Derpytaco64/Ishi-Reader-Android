package com.ishireader.app.data.model

import java.net.URLDecoder

/**
 * Ishi-Read's /api/books returns `url` as `/read/manifest/<urlencoded manifestUrl>` -- a route
 * for its own web reader, not the manifest itself. The encoded segment is the absolute URL to
 * the underlying Readium Web Publication Server manifest.json, which is what both the Readium
 * navigator (to open the book) and /api/userdata/position (to key the saved locator) need.
 */
fun Book.manifestUrl(): String =
    URLDecoder.decode(url.substringAfter("/read/manifest/"), "UTF-8")

/** Mirrors the website's `displayed.rendition === "Comic"` (StatefulBookSheet.tsx) -- "Comic" is
 *  the only other value api/books/route.ts ever sets [Book.rendition] to besides "Audiobook", but a
 *  named check reads clearer at call sites than a bare string comparison. */
val Book.isComic: Boolean get() = rendition == "Comic"
