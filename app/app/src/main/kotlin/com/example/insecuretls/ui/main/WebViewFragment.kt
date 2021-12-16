package com.example.insecuretls.ui.main

import android.net.http.SslError
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import com.example.insecuretls.databinding.FragmentWebviewBinding
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

class WebViewFragment : Fragment() {

    private lateinit var implementation: Implementation
    private var _binding: FragmentWebviewBinding? = null

    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        implementation = Implementation.values()[arguments?.getInt(ARG_SECTION_NUMBER) ?: 0]
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentWebviewBinding.inflate(inflater, container, false)

        when (implementation) {
            Implementation.WEBVIEW_IGNORING_TLS_ERRORS -> setupInsecureWebView()
            Implementation.TLS_CERTIFICATE_CHECK_DISABLED -> setupInsecureHostnameVerifier()
            Implementation.MALFUNCTIONING_X509_TRUST_MANAGER -> setupInsecureTrustManager()
        }

        return binding.root
    }

    /**
     * Set up the WebView to ignore all SSL errors.
     */
    private fun setupInsecureWebView() {
        binding.webview.webViewClient = object : WebViewClient() {
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                handler?.proceed()
            }
        }

        binding.fab.setOnClickListener {
            if (binding.webview.url == null) {
                binding.webview.loadUrl("https://www.mitmtest.com")
            } else {
                binding.webview.clearCache(true)
                binding.webview.reload()
            }
        }
    }

    /**
     * Disable the host name checking for SSL certificates.
     */
    private fun setupInsecureHostnameVerifier() {
        binding.fab.setOnClickListener {
            val url = URL("https://wrong.host.badssl.com")
            val connection = url.openConnection() as HttpsURLConnection
            connection.hostnameVerifier = HostnameVerifier { hostname, session -> true }

            GlobalScope.async {
                val content = connection.inputStream.bufferedReader().use { it.readText() }

                withContext(Main) {
                    binding.webview.loadData(
                        Base64.encodeToString(
                            content.toByteArray(),
                            Base64.NO_PADDING
                        ),
                        "text/html",
                        "base64"
                    )
                }
            }
        }
    }

    /**
     * Create a malfunctioning X509TrustManager implementation.
     */
    private fun setupInsecureTrustManager() {
        binding.fab.setOnClickListener {
            val insecureTrustManager = object : X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?
                ) {
                    // do nothing
                }

                override fun checkServerTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?
                ) {
                    // do nothing
                }

                override fun getAcceptedIssuers() = null
            }
            val context = SSLContext.getInstance(if (SDK_INT > 28) "TLSv1.3" else "TLSv1.2")
            context.init(null, arrayOf(insecureTrustManager), SecureRandom())

            val url = URL("https://www.mitmtest.com")
            val connection = url.openConnection() as HttpsURLConnection
            connection.sslSocketFactory = context.socketFactory

            GlobalScope.async {
                val content = connection.inputStream.bufferedReader().use { it.readText() }

                withContext(Main) {
                    binding.webview.loadData(
                        Base64.encodeToString(
                            content.toByteArray(),
                            Base64.NO_PADDING
                        ),
                        "text/html",
                        "base64"
                    )
                }
            }
        }
    }

    companion object {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private const val ARG_SECTION_NUMBER = "section_number"

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        @JvmStatic
        fun newInstance(sectionNumber: Int): WebViewFragment {
            return WebViewFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_SECTION_NUMBER, sectionNumber)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}