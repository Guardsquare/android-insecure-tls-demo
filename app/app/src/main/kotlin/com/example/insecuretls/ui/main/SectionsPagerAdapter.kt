package com.example.insecuretls.ui.main

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter

enum class Implementation(val title: String) {
    WEBVIEW_IGNORING_TLS_ERRORS("Insecure WebViewClient"),
    TLS_CERTIFICATE_CHECK_DISABLED("Insecure HostnameVerifier"),
    MALFUNCTIONING_X509_TRUST_MANAGER("Insecure X509TrustManager"),
    NETWORK_SECURITY_CONFIG("Network Security Config"),
    CUSTOM_CA_LEGACY("Custom CA: Trust Manager"),
    CUSTOM_CA_LEGACY_WEBVIEW("Custom CA: WebView"),
    PINNING_LEGACY("Pinning: OkHttp"),
    PINNING_LEGACY_WEBVIEW("Pinning: WebViews")
}

/**
 * A [FragmentPagerAdapter] that returns a fragment corresponding to
 * one of the sections/tabs/pages.
 */
class SectionsPagerAdapter(private val context: Context, fm: FragmentManager) :
    FragmentPagerAdapter(fm) {

    override fun getItem(position: Int): Fragment {
        // getItem is called to instantiate the fragment for the given page.
        // Return a PlaceholderFragment (defined as a static inner class below).
        return WebViewFragment.newInstance(position)
    }

    override fun getPageTitle(position: Int): CharSequence {
        return Implementation.values()[position].title
    }

    override fun getCount(): Int {
        return Implementation.values().size
    }
}