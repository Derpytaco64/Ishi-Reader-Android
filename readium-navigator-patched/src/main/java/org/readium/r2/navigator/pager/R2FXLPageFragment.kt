/*
 * Module: r2-navigator-kotlin
 * Developers: Aferdita Muriqi, Clément Baumann, Mostapha Idoubihi, Paul Stoica
 *
 * Copyright (c) 2018. Readium Foundation. All rights reserved.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.navigator.pager

import android.annotation.SuppressLint
import android.graphics.PointF
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.webkit.WebViewClientCompat
import org.readium.r2.navigator.R2BasicWebView
import org.readium.r2.navigator.databinding.ReadiumNavigatorFragmentFxllayoutDoubleBinding
import org.readium.r2.navigator.databinding.ReadiumNavigatorFragmentFxllayoutSingleBinding
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubNavigatorViewModel
import org.readium.r2.navigator.epub.fxl.R2FXLLayout
import org.readium.r2.navigator.epub.fxl.R2FXLOnDoubleTapListener
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.util.Url

internal class R2FXLPageFragment : Fragment() {

    private val firstResourceUrl: Url?
        get() = BundleCompat.getParcelable(requireArguments(), "firstUrl", Url::class.java)

    private val secondResourceUrl: Url?
        get() = BundleCompat.getParcelable(requireArguments(), "secondUrl", Url::class.java)

    private val firstResourceLink: Link?
        get() = BundleCompat.getParcelable(requireArguments(), "firstLink", Link::class.java)

    private val secondResourceLink: Link?
        get() = BundleCompat.getParcelable(requireArguments(), "secondLink", Link::class.java)

    private var webViews = mutableListOf<R2BasicWebView>()

    private var _doubleBinding: ReadiumNavigatorFragmentFxllayoutDoubleBinding? = null
    private val doubleBinding get() = _doubleBinding!!

    private var _singleBinding: ReadiumNavigatorFragmentFxllayoutSingleBinding? = null
    private val singleBinding get() = _singleBinding!!

    private val navigator: EpubNavigatorFragment?
        get() = parentFragment as? EpubNavigatorFragment

    private val viewModel: EpubNavigatorViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        secondResourceUrl?.let {
            _doubleBinding = ReadiumNavigatorFragmentFxllayoutDoubleBinding.inflate(
                inflater,
                container,
                false
            )
            val view: View = doubleBinding.root
            view.setPadding(0, 0, 0, 0)

            val r2FXLLayout = doubleBinding.r2FXLLayout
            r2FXLLayout.isAllowParentInterceptOnScaled = true

            val left = doubleBinding.firstWebView
            val right = doubleBinding.secondWebView

            setupWebView(left, firstResourceLink, firstResourceUrl, objectPosition = "right center")
            setupWebView(right, secondResourceLink, secondResourceUrl, objectPosition = "left center")

            r2FXLLayout.addOnDoubleTapListener(R2FXLOnDoubleTapListener(true))
            r2FXLLayout.addOnTapListener(object : R2FXLLayout.OnTapListener {
                override fun onTap(view: R2FXLLayout, info: R2FXLLayout.TapInfo): Boolean {
                    return left.listener?.onTap(PointF(info.x, info.y)) ?: false
                }
            })

            return view
        } ?: run {
            _singleBinding = ReadiumNavigatorFragmentFxllayoutSingleBinding.inflate(
                inflater,
                container,
                false
            )
            val view: View = singleBinding.root
            view.setPadding(0, 0, 0, 0)

            val r2FXLLayout = singleBinding.r2FXLLayout
            r2FXLLayout.isAllowParentInterceptOnScaled = true

            val webview = singleBinding.webViewSingle

            setupWebView(webview, firstResourceLink, firstResourceUrl)

            r2FXLLayout.addOnDoubleTapListener(R2FXLOnDoubleTapListener(true))
            r2FXLLayout.addOnTapListener(object : R2FXLLayout.OnTapListener {
                override fun onTap(view: R2FXLLayout, info: R2FXLLayout.TapInfo): Boolean {
                    return webview.listener?.onTap(PointF(info.x, info.y)) ?: false
                }
            })

            return view
        }
    }

    override fun onDetach() {
        super.onDetach()

        // Prevent the web views from leaking when their parent is detached.
        // See https://stackoverflow.com/a/19391512/1474476
        for (wv in webViews) {
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.removeAllViews()
            wv.destroy()
        }
    }

    override fun onDestroyView() {
        for (webView in webViews) {
            webView.listener = null
        }
        _singleBinding = null
        _doubleBinding = null

        super.onDestroyView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(
        webView: R2BasicWebView,
        link: Link?,
        resourceUrl: Url?,
        objectPosition: String = "center",
    ) {
        webViews.add(webView)
        navigator?.let {
            webView.listener = it.webViewListener
        }

        webView.settings.javaScriptEnabled = true
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.setSupportZoom(true)
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false
        // If we don't explicitly override the [textZoom], it will be set by Android's
        // accessibility font size system setting which breaks the layout of some fixed layouts.
        // See https://github.com/readium/kotlin-toolkit/issues/76
        webView.settings.textZoom = 100

        webView.setInitialScale(1)

        webView.setPadding(0, 0, 0, 0)
        webView.addJavascriptInterface(webView, "Android")

        webView.webViewClient = object : WebViewClientCompat() {

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                (webView as? R2BasicWebView)?.shouldOverrideUrlLoading(request) ?: false

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                (webView as? R2BasicWebView)?.shouldInterceptRequest(view, request)

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                if (link != null) {
                    webView.listener?.onResourceLoaded(webView, link)
                    webView.listener?.onPageLoaded(webView, link)
                }
            }
        }
        webView.isHapticFeedbackEnabled = false
        webView.isLongClickable = false
        webView.setOnLongClickListener {
            true
        }

        // CLAUDE-ADDED: Comic/manga readingOrder links point straight at raw page images (see
        // FixedLayoutComicPublicationParser's doc comment), so loading them via webView.loadUrl()
        // hands the image to WebView's built-in image viewer, which zooms to fit the viewport
        // *width* only. In landscape ("horizontal") orientation a portrait manga page then gets
        // scaled up until it fills the screen width, cropping the top and bottom off-screen.
        // Wrapping the image in a tiny HTML/CSS shell with object-fit: contain instead fits it
        // within both dimensions, so the whole page is always visible (letterboxed rather than
        // cropped) regardless of device orientation.
        //
        // CLAUDE-ADDED: For a two-page spread, each half is fit independently, so if the object-fit
        // shrinkage is height-constrained the image ends up narrower than its half and, by default,
        // centered -- leaving a visible gap at the spine where the two pages should meet. objectPosition
        // pins each image to the shared inner edge instead (the left page hugs the right edge of its
        // box, the right page hugs the left edge of its box) so the spread always stays flush.
        resourceUrl?.let { url ->
            val src = url.toString().replace("&", "&amp;")
            // CLAUDE-ADDED: dark mode for comics has never recolored the page itself -- it just paints
            // the R2ViewPager behind these fragments with the theme color (see
            // EpubNavigatorFragment.effectiveBackgroundColor) and lets it show through any letterboxing.
            // The old direct loadUrl(imageUrl) used WebView's built-in "image document" viewer, which has
            // no opaque page background of its own. This wrapper is a real HTML document though, and an
            // HTML page defaults to an opaque white background -- so without an explicit color here, the
            // object-fit letterbox bars paint white over the dark pager behind them regardless of theme.
            // Match the wrapper's background to the same effective color so letterboxing stays themed.
            val settings = viewModel.settings.value
            val backgroundColorInt = settings.backgroundColor?.int ?: settings.theme.backgroundColor
            val backgroundHex = String.format("#%06X", 0xFFFFFF and backgroundColorInt)
            webView.setBackgroundColor(backgroundColorInt)
            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    html, body {
                        margin: 0;
                        padding: 0;
                        width: 100%;
                        height: 100%;
                        overflow: hidden;
                        background-color: $backgroundHex;
                    }
                    img {
                        display: block;
                        width: 100%;
                        height: 100%;
                        object-fit: contain;
                        object-position: $objectPosition;
                    }
                </style>
                </head>
                <body>
                <img src="$src">
                </body>
                </html>
            """.trimIndent()
            webView.loadDataWithBaseURL(url.toString(), html, "text/html", "utf-8", null)
        }
    }

    companion object {

        fun newInstance(left: Pair<Link, Url>?, right: Pair<Link, Url>? = null): R2FXLPageFragment =
            R2FXLPageFragment().apply {
                arguments = Bundle().apply {
                    putParcelable("firstLink", left?.first)
                    putParcelable("firstUrl", left?.second)
                    putParcelable("secondLink", right?.first)
                    putParcelable("secondUrl", right?.second)
                }
            }
    }
}
