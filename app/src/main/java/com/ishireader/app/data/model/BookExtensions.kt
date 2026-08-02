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
