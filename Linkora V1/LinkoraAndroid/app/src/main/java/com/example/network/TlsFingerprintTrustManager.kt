package com.example.network

import com.example.protocol.ChecksumUtils
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Custom X509TrustManager that explicitly pins against the certificate SHA-256 fingerprint
 * obtained through the secure out-of-band QR pairing payload.
 *
 * This guarantees strict identity pinning: only the specific Windows desktop TLS certificate
 * approved during the pairing exchange will ever be accepted. If the certificate changes,
 * connection is immediately aborted, protecting against rogue local networks and MITM attacks.
 */
class TlsFingerprintTrustManager(
    private val expectedFingerprintHex: String
) : X509TrustManager {

    private val normalizedExpected = expectedFingerprintHex.replace(":", "").trim().lowercase()

    init {
        require(normalizedExpected.length == 64) {
            "Expected SHA-256 certificate fingerprint must be 64 hexadecimal characters"
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        // Not used - Android acts as client
    }

    @Throws(CertificateException::class)
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (chain.isNullOrEmpty()) {
            throw CertificateException("Server presented an empty certificate chain")
        }

        val serverLeafCert = chain[0]
        val certDerBytes = serverLeafCert.encoded
        val actualFingerprint = ChecksumUtils.sha256Hex(certDerBytes).lowercase()

        if (actualFingerprint != normalizedExpected) {
            throw CertificateException(
                "Linkora TLS pinning failure: Certificate fingerprint mismatch. " +
                        "Expected: $normalizedExpected, Present: $actualFingerprint"
            )
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()

    companion object {
        fun createSslSocketFactory(fingerprintHex: String): Pair<SSLSocketFactory, X509TrustManager> {
            val trustManager = TlsFingerprintTrustManager(fingerprintHex)
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(trustManager), null)
            return Pair(sslContext.socketFactory, trustManager)
        }

        /**
         * HostnameVerifier that binds with our pinned certificate check.
         * Since local network PCs use dynamic LAN IP addresses, identity is authenticated
         * strictly via the pinned cryptographic certificate fingerprint rather than DNS names.
         */
        val PinnedIdentityHostnameVerifier = HostnameVerifier { _, _ -> true }
    }
}
