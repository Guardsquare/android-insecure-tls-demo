package com.example.insecuretls.ui.main

import android.net.http.SslError
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.insecuretls.R
import com.example.insecuretls.databinding.FragmentWebviewBinding
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.net.URL
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.*

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
            Implementation.NETWORK_SECURITY_CONFIG -> setupNetworkSecurityConfig()
            Implementation.CUSTOM_CA_LEGACY -> setupCustomCaLegacy()
            Implementation.CUSTOM_CA_LEGACY_WEBVIEW -> setupCustomCaLegacyWebview()
            Implementation.PINNING_LEGACY -> setupPinningLegacy()
            Implementation.PINNING_LEGACY_WEBVIEW -> setupPinningLegacyWebview()
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
            val context = SSLContext.getInstance(TLS_VERSION)
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

    /**
     * Setup the WebView without any custom trust manager, to showcase how the network security
     * configuration xml works without code changes.
     */
    private fun setupNetworkSecurityConfig() {
        binding.webview.webViewClient = object : WebViewClient() {
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                val cause = when (error?.primaryError) {
                    SslError.SSL_NOTYETVALID -> "The certificate is only valid after ${error.certificate.validNotBeforeDate}"
                    SslError.SSL_EXPIRED -> "The certificate has expired on ${error.certificate.validNotAfterDate}"
                    SslError.SSL_IDMISMATCH -> "Hostname mismatch (url ${view?.url} but certificate is for ${error.certificate.issuedTo.cName})"
                    SslError.SSL_UNTRUSTED -> "The certificate ${error.certificate} is not trusted"
                    SslError.SSL_DATE_INVALID -> "Date is invalid"
                    else -> "An unknown error occurred"
                }
                if (context != null) {
                    val builder = AlertDialog.Builder(context!!)
                    builder.apply {
                        setTitle("SSL Error")
                        setMessage(
                            "Error validating server certificate: $cause\n" +
                                    "Attackers might want to steal your data."
                        )
                        setNegativeButton("Abort") { _, _ -> handler?.cancel() }
                    }
                    builder.create().show()
                } else {
                    handler?.cancel()
                }
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
     * Create a trust manager that accepts a custom CA certificate in addition to all
     * certificates in the system trust store.
     */
    private fun setupCustomCaLegacy() {
        binding.fab.setOnClickListener {
            val tmf = initTrustManagerFactory()
            val sslContext = SSLContext.getInstance(TLS_VERSION)
            sslContext.init(null, tmf.trustManagers, null)

            val defaultSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory()
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)

            val url = URL("https://www.mitmtest.com")
            val connection = url.openConnection() as HttpsURLConnection

            GlobalScope.async {
                var content: String
                try {
                    content = connection.inputStream.bufferedReader().use { it.readText() }
                } catch (e: SSLException) {
                    content = ""
                    if (context != null) {
                        withContext(Main) {
                            val builder = AlertDialog.Builder(context!!)
                            builder.apply {
                                setTitle("SSL Exception")
                                setMessage(e.message)
                                setNegativeButton(R.string.abort) { _, _ -> }
                            }
                            builder.create().show()
                        }
                    }
                }

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
            HttpsURLConnection.setDefaultSSLSocketFactory(defaultSocketFactory)
        }
    }

    /**
     * Create a trust manager that is used to accept a custom CA certificate in a WebView.
     */
    private fun setupCustomCaLegacyWebview() {
        val tmf = initTrustManagerFactory()

        binding.webview.webViewClient = object : WebViewClient() {
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                val cause = when (error?.primaryError) {
                    SslError.SSL_NOTYETVALID -> "The certificate is only valid after ${error.certificate.validNotBeforeDate}"
                    SslError.SSL_EXPIRED -> "The certificate has expired on ${error.certificate.validNotAfterDate}"
                    SslError.SSL_IDMISMATCH -> "Hostname mismatch (url ${view?.url} but certificate is for ${error.certificate.issuedTo.cName})"
                    SslError.SSL_UNTRUSTED -> {
                        try {
                            val certField =
                                error.certificate.javaClass.getDeclaredField("mX509Certificate")
                            certField.isAccessible = true
                            val cert = certField.get(error.certificate) as X509Certificate
                            tmf.trustManagers.forEach {
                                (it as X509TrustManager).checkServerTrusted(
                                    arrayOf(cert), "generic"
                                )
                            }
                            handler?.proceed()
                            return
                        } catch (e: Exception) {
                            "The certificate ${error.certificate} is not trusted"
                        }
                    }
                    SslError.SSL_DATE_INVALID -> "Date is invalid"
                    else -> "An unknown error occurred"
                }
                if (context != null) {
                    val builder = AlertDialog.Builder(context!!)
                    builder.apply {
                        setTitle("SSL Error")
                        setMessage(
                            "Error validating server certificate: $cause\n" +
                                    "Attackers might want to steal your data."
                        )
                        setNegativeButton(R.string.abort) { _, _ -> handler?.cancel() }
                    }
                    builder.create().show()
                } else {
                    handler?.cancel()
                }
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
     * Enforce certificate pinning for the server certificate.
     */
    private fun setupPinningLegacy() {
        binding.fab.setOnClickListener {
            val tmf = initTrustManagerFactory()
            val sslContext = SSLContext.getInstance(TLS_VERSION)
            sslContext.init(null, tmf.trustManagers, null)

            val pinner = CertificatePinner.Builder()
                .add("*.mitmtest.com", "sha256/pcG7tltpGuaJrssJiqr5bmYc4iypr3QE65su1XuDcK8=")
                .build()
            val httpClient = OkHttpClient.Builder()
                // Custom socket factory is only needed because the pinned certificate is from a custom CA
                .sslSocketFactory(sslContext.socketFactory, tmf.trustManagers[0] as X509TrustManager)
                .certificatePinner(pinner)
                .build()
            val url = URL("https://www.mitmtest.com")
            val request = Request.Builder().url(url).build()

            GlobalScope.async {
                var content = ""
                try {
                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        content = response.body?.string() ?: ""
                    }
                } catch (e: SSLException) {
                    if (context != null) {
                        withContext(Main) {
                            val builder = AlertDialog.Builder(context!!)
                            builder.apply {
                                setTitle("SSL Exception")
                                setMessage(e.message)
                                setNegativeButton(R.string.abort) { _, _ -> }
                            }
                            builder.create().show()
                        }
                    }
                }

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

    object ContentTypeParser {
        private const val PARTS_DELIMITER = ";"
        private const val VALUE_DELIMITER = "="
        private const val UTF8 = "UTF-8"

        private const val CHARSET = "charset"

        fun getMimeType(contentType: String): String {
            if (contentType.contains(PARTS_DELIMITER)) {
                val contentTypeParts = contentType.split(PARTS_DELIMITER.toRegex())
                return contentTypeParts[0].trim()
            }
            return contentType
        }

        fun getCharset(contentType: String): String {
            if (contentType.contains(PARTS_DELIMITER)) {
                val contentTypeParts = contentType.split(PARTS_DELIMITER.toRegex())
                val charsetParts = contentTypeParts[1].split(VALUE_DELIMITER.toRegex())
                if (charsetParts[0].trim().startsWith(CHARSET)) {
                    return charsetParts[1].trim().toUpperCase()
                }
            }
            return UTF8
        }
    }

    /**
     * Add certificate pinning to a WebView.
     */
    private fun setupPinningLegacyWebview() {
        val tmf = initTrustManagerFactory()
        val sslContext = SSLContext.getInstance(TLS_VERSION)
        sslContext.init(null, tmf.trustManagers, null)

        val pinner = CertificatePinner.Builder()
            .add("*.mitmtest.com", "sha256/pcG7tltpGuaJrssJiqr5bmYc4iypr3QE65su1XuDcK8=")
            .build()
        val httpClient = OkHttpClient.Builder()
            // Custom socket factory is only needed because the pinned certificate is from a custom CA
            .sslSocketFactory(sslContext.socketFactory, tmf.trustManagers[0] as X509TrustManager)
            .certificatePinner(pinner)
            .build()

        binding.webview.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                interceptedRequest: WebResourceRequest
            ): WebResourceResponse {
                try {
                    val url = URL(interceptedRequest.url.toString())
                    val request = Request.Builder().url(url).build()
                    val response = httpClient.newCall(request).execute()

                    val contentType = response.header("Content-Type")

                    if (contentType != null) {

                        val inputStream = response.body?.byteStream()
                        val mimeType = ContentTypeParser.getMimeType(contentType)
                        val charset = ContentTypeParser.getCharset(contentType)

                        return WebResourceResponse(mimeType, charset, inputStream)
                    }
                } catch (e: SSLPeerUnverifiedException) {
                    Handler(Looper.getMainLooper()).post {
                        val builder = AlertDialog.Builder(context!!)
                        builder.apply {
                            setTitle("Certificate Error")
                            setMessage(e.message)
                            setNegativeButton(R.string.abort) { _, _ -> }
                        }
                        builder.create().show()
                    }
                } catch (e: Exception) {
                    Handler(Looper.getMainLooper()).post {
                        val builder = AlertDialog.Builder(context!!)
                        builder.apply {
                            setTitle("Connection Error")
                            setMessage(e.message ?: "Cause unknown")
                            setNegativeButton(R.string.abort) { _, _ -> }
                        }
                        builder.create().show()
                    }
                }

                return WebResourceResponse(null, null, null)
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

    private fun initTrustManagerFactory(): TrustManagerFactory {
        // Parse CA certificate from the res/raw/my_ca.crt resource
        val cf = CertificateFactory.getInstance("X.509")
        val caInput = BufferedInputStream(resources.openRawResource(R.raw.mitmtest_ca))
        val ca = caInput.use {
            cf.generateCertificate(it)
        }

        // Create key store and insert the custom certificate
        val keyStoreType = KeyStore.getDefaultType()
        val keyStore = KeyStore.getInstance(keyStoreType)
        keyStore.load(null, null)
        keyStore.setCertificateEntry("custom_ca", ca)

        // Add the default well known certificates as well
        val tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm()
        val defaultTmf = TrustManagerFactory.getInstance(tmfAlgorithm)
        defaultTmf.init(null as KeyStore?)
        defaultTmf.trustManagers.filterIsInstance<X509TrustManager>()
            .flatMap { it.acceptedIssuers.toList() }
            .forEach { keyStore.setCertificateEntry(it.subjectDN.name, it) }

        // Create a new trust manager that uses this custom key store
        val tmf = TrustManagerFactory.getInstance(tmfAlgorithm)
        tmf.init(keyStore)
        return tmf
    }

    companion object {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private const val ARG_SECTION_NUMBER = "section_number"

        /**
         * The currently supported TLS version (SDK 29 and above: 1.3, everything below only
         * supports 1.2).
         */
        private val TLS_VERSION = if (SDK_INT > 28) "TLSv1.3" else "TLSv1.2"

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