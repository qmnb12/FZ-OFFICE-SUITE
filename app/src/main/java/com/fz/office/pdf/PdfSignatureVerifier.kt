package com.fz.office.pdf

import android.content.Context
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.math.BigInteger
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

/**
 * One signature as extracted by the bundled PDF.js viewer (signed byte ranges plus the
 * raw PKCS#7 blob). The viewer only reads these; all checks below run in Kotlin so a
 * malformed signature can never be painted as valid.
 */
data class PdfRawSignature(
    val name: String,
    val reason: String,
    val location: String,
    val date: String,
    val subFilter: String,
    val coversWhole: Boolean,
    val modifications: Int,
    val byteRange: LongArray,
    val contents: ByteArray,
    val fieldName: String = ""
)

data class PdfSignatureReport(
    val valid: Boolean,
    val trusted: Boolean,
    val wholeDocument: Boolean,
    val signer: String?,
    val issuer: String?,
    val signedAt: String?,
    val reason: String?,
    val location: String?,
    val algorithm: String?,
    val note: String?,
    val signedAtAdobe: String? = null,
    val diagnostic: String? = null,
    val fieldName: String? = null
)

/**
 * Offline verification of embedded PDF signatures (adbe.pkcs7.detached and
 * ETSI.CAdES.detached). No code is executed from the PDF and no network is used:
 *
 * 1. the CMS SignedData is parsed from /Contents;
 * 2. the messageDigest attribute is compared with a fresh digest of the exact
 *    /ByteRange bytes, so any edit outside the signature invalidates it;
 * 3. the signer's public key verifies the signature over the signed attributes;
 * 4. the certificate chain is validated against the device trust anchors.
 *
 * A signature is only reported trusted when the whole chain validates. Unknown or
 * self-signed issuers stay "valid but not trusted", which is what desktop readers
 * call an unknown signature identity.
 */
object PdfSignatureVerifier {

    private const val MAX_HASH_BYTES = 512L * 1024L * 1024L

    fun verify(context: Context, uri: Uri, raw: PdfRawSignature): PdfSignatureReport {
        val ranges = raw.byteRange
        if (ranges.size < 4) {
            return failure("Signature has no valid byte range.")
        }
        val signedBytes = ranges[1] + ranges[3]
        if (signedBytes <= 0L || signedBytes > MAX_HASH_BYTES) {
            return failure("Signature covers an unsupported document size.")
        }

        val parsed = try {
            parseSignedData(raw.contents)
        } catch (error: Exception) {
            return failure("Signature data could not be read.")
        }
        val signer = parsed.signerCertificate
            ?: return failure("Signer certificate is missing from the signature.")
        val digestName = digestName(parsed.digestOid)
            ?: return failure("Unsupported digest algorithm.")
        val jcaName = signatureAlgorithm(parsed.signatureOid, digestName)
            ?: return failure("Unsupported signature algorithm.")

        val signedAtMillis = parsed.signingTimeMillis ?: parsePdfDate(raw.date)
        val signedAt = formatDate(signedAtMillis) ?: raw.date.takeIf { it.isNotBlank() }
        val signedAtAdobe = formatAdobeDate(raw.date, signedAtMillis)

        val signedAttributes = parsed.signedAttributes
        val expectedDigest = parsed.messageDigest
        val embeddedContent = parsed.embeddedContent
        val valid: Boolean = try {
            if (signedAttributes != null && expectedDigest != null) {
                // The signed attribute covers either the embedded content
                // (adbe.pkcs7.sha1) or the byte ranges (detached signatures).
                val actual = embeddedContent ?: hashRanges(context, uri, ranges, digestName)
                if (!MessageDigest.isEqual(actual, expectedDigest)) {
                    false
                } else {
                    val signedBytesForVerification = signedAttributes.copyOf()
                    signedBytesForVerification[0] = 0x31
                    val verifier = Signature.getInstance(jcaName)
                    verifier.initVerify(signer.publicKey)
                    verifier.update(signedBytesForVerification)
                    verifier.verify(parsed.signature)
                }
            } else if (embeddedContent != null) {
                // adbe.pkcs7.sha1: the CMS content is the digest of the byte
                // ranges, so bind it to the file and sign that digest.
                val expected = hashRanges(context, uri, ranges, digestName)
                if (embeddedContent.size != expected.size ||
                    !MessageDigest.isEqual(embeddedContent, expected)
                ) {
                    false
                } else {
                    val verifier = Signature.getInstance(jcaName)
                    verifier.initVerify(signer.publicKey)
                    verifier.update(embeddedContent)
                    verifier.verify(parsed.signature)
                }
            } else {
                val verifier = Signature.getInstance(jcaName)
                verifier.initVerify(signer.publicKey)
                consumeRanges(context, uri, ranges) { buffer, length ->
                    verifier.update(buffer, 0, length)
                }
                verifier.verify(parsed.signature)
            }
        } catch (error: Exception) {
            false
        }

        val trust = if (valid) {
            evaluateTrust(context, signer, parsed.certificates, signedAtMillis?.let { Date(it) })
        } else {
            TrustResult(false, null)
        }
        val trusted = trust.trusted
        val whole = raw.coversWhole && raw.modifications <= 0

        return PdfSignatureReport(
            valid = valid,
            trusted = trusted,
            wholeDocument = whole,
            signer = commonName(signer.subjectX500Principal),
            issuer = commonName(signer.issuerX500Principal),
            signedAt = signedAt,
            signedAtAdobe = signedAtAdobe,
            reason = raw.reason.takeIf { it.isNotBlank() },
            location = raw.location.takeIf { it.isNotBlank() },
            algorithm = "$digestName · ${if (jcaName.endsWith("ECDSA")) "ECDSA" else "RSA"}",
            note = when {
                !valid -> "The signature does not match the document bytes."
                !whole -> "The document was changed after it was signed."
                trusted -> "Certificate chain is trusted on this device."
                else -> "Signature is intact, but the issuer is not in the device trust store."
            },
            diagnostic = trust.diagnostic
        )
    }

    private fun failure(note: String) = PdfSignatureReport(
        valid = false,
        trusted = false,
        wholeDocument = false,
        signer = null,
        issuer = null,
        signedAt = null,
        reason = null,
        location = null,
        algorithm = null,
        note = note
    )

    private class ParsedSignature(
        val digestOid: String,
        val signatureOid: String,
        val signature: ByteArray,
        val signedAttributes: ByteArray?,
        val messageDigest: ByteArray?,
        val embeddedContent: ByteArray?,
        val signingTimeMillis: Long?,
        val signerCertificate: X509Certificate?,
        val certificates: List<X509Certificate>
    )

    private fun parseSignedData(contents: ByteArray): ParsedSignature {
        val der = Der(contents)
        val contentInfo = der.read()
        val contentInfoChildren = der.children(contentInfo)
        require(contentInfoChildren.size >= 2) { "ContentInfo" }
        require(der.oid(contentInfoChildren[0]) == OID_SIGNED_DATA) { "Not CMS SignedData" }
        val signedDataWrapper = contentInfoChildren[1]
        val signedData = der.children(signedDataWrapper).firstOrNull() ?: throw IllegalArgumentException()
        val signedDataChildren = der.children(signedData)
        require(signedDataChildren.size >= 4) { "SignedData" }

        val certificateFactory = CertificateFactory.getInstance("X.509")
        val certificates = ArrayList<X509Certificate>()
        var signerInfoSet: Der.El? = null
        for (child in signedDataChildren) {
            when (child.tag) {
                0xA0 -> {
                    for (item in der.children(child)) {
                        runCatching {
                            certificateFactory.generateCertificate(
                                ByteArrayInputStream(der.raw(item))
                            ) as X509Certificate
                        }.getOrNull()?.let { certificates.add(it) }
                    }
                }
                0xA1 -> Unit
                0x31 -> signerInfoSet = child
            }
        }
        val signerInfo = signerInfoSet?.let { der.children(it).firstOrNull() }
            ?: throw IllegalArgumentException("SignerInfo")
        val info = der.children(signerInfo)
        var index = 0
        require(info.size >= 5) { "SignerInfo fields" }
        index++ // version
        val sid = info[index++]
        val digestAlgorithm = info[index++]
        val digestOid = der.oid(der.children(digestAlgorithm).first())
        val signedAttributes = if (info[index].tag == 0xA0) info[index++] else null
        val signatureAlgorithm = info[index++]
        val signatureOid = der.oid(der.children(signatureAlgorithm).first())
        val signature = der.octets(info[index])
        require(signature.isNotEmpty()) { "Signature value" }

        var messageDigest: ByteArray? = null
        var signingTimeMillis: Long? = null
        if (signedAttributes != null) {
            for (attribute in der.children(signedAttributes)) {
                val attributeChildren = der.children(attribute)
                if (attributeChildren.isEmpty()) continue
                val type = der.oid(attributeChildren[0])
                val valueSet = attributeChildren.getOrNull(1) ?: continue
                val value = der.children(valueSet).firstOrNull() ?: continue
                when (type) {
                    OID_MESSAGE_DIGEST -> messageDigest = der.octets(value)
                    OID_SIGNING_TIME -> signingTimeMillis = der.asn1Time(value)
                }
            }
        }

        var embeddedContent: ByteArray? = null
        val encapContentInfo = signedDataChildren.firstOrNull { it.tag == 0x30 }
        if (encapContentInfo != null) {
            val encapFields = der.children(encapContentInfo)
            val wrapper = encapFields.getOrNull(1)
            if (wrapper != null && wrapper.tag == 0xA0) {
                val inner = der.children(wrapper).firstOrNull()
                if (inner != null) embeddedContent = der.octets(inner)
            }
        }

        val signerCertificate = findSignerCertificate(der, sid, certificates)
        return ParsedSignature(
            digestOid = digestOid,
            signatureOid = signatureOid,
            signature = signature,
            signedAttributes = signedAttributes?.let { der.raw(it) },
            messageDigest = messageDigest,
            embeddedContent = embeddedContent,
            signingTimeMillis = signingTimeMillis,
            signerCertificate = signerCertificate,
            certificates = certificates
        )
    }

    private fun findSignerCertificate(
        der: Der,
        sid: Der.El,
        certificates: List<X509Certificate>
    ): X509Certificate? {
        if (sid.tag == 0x30) {
            val parts = der.children(sid)
            if (parts.size >= 2) {
                val issuerBytes = der.raw(parts[0])
                val serial = der.integer(parts[1])
                certificates.firstOrNull {
                    it.serialNumber == serial &&
                        it.issuerX500Principal.encoded.contentEquals(issuerBytes)
                }?.let { return it }
            }
        } else if (sid.tag == 0xA0) {
            val keyId = der.octets(sid)
            certificates.firstOrNull { certificate ->
                val extension = certificate.getExtensionValue("2.5.29.14") ?: return@firstOrNull false
                val inner = runCatching {
                    val outer = Der(extension)
                    val innerDer = outer.octets(outer.read())
                    Der(innerDer).let { d -> d.octets(d.read()) }
                }.getOrNull()
                inner != null && inner.contentEquals(keyId)
            }?.let { return it }
        }
        return certificates.singleOrNull()
    }

    private data class TrustResult(val trusted: Boolean, val diagnostic: String?)

    private fun evaluateTrust(
        context: Context,
        signer: X509Certificate,
        certificates: List<X509Certificate>,
        at: Date?
    ): TrustResult {
        val bundled = runCatching { loadBundledTrust(context) }.getOrNull()
        val systemAnchors = runCatching {
            val factory = TrustManagerFactory.getInstance("X509")
            factory.init(null as KeyStore?)
            factory.trustManagers
                .filterIsInstance<X509TrustManager>()
                .flatMap { it.acceptedIssuers.toList() }
        }.getOrDefault(emptyList())
        val bundledRoots = bundled?.first ?: emptyList()
        val bundledIntermediates = bundled?.second ?: emptyList()
        val anchors = systemAnchors + bundledRoots
        val pool = certificates + bundledIntermediates
        val moment = (at ?: Date()).time
        val trusted = chainToAnchor(signer, pool, anchors, moment, 0, 0)
        if (trusted) return TrustResult(true, null)
        val detail = buildString {
            append("Trust check: roots=").append(bundledRoots.size)
            append(", intermediates=").append(bundledIntermediates.size)
            append(", system=").append(systemAnchors.size)
            append(", chain=").append(signer.subjectX500Principal.name.take(40))
        }
        return TrustResult(false, detail)
    }

    /**
     * Walk the chain by hand: Android's PKIX provider does not offer a
     * CertPathBuilder on every version, and some CAs encode the same DN with
     * different string types. A hop is accepted when the candidate's key
     * verifies the child's signature, so the cryptographic binding decides,
     * not a textual name comparison.
     */
    private fun chainToAnchor(
        certificate: X509Certificate,
        pool: List<X509Certificate>,
        anchors: List<X509Certificate>,
        moment: Long,
        depth: Int,
        caDepth: Int
    ): Boolean {
        if (depth > 8) return false
        if (moment < certificate.notBefore.time || moment > certificate.notAfter.time) {
            return false
        }
        for (anchor in anchors) {
            if (certificate.encoded.contentEquals(anchor.encoded)) {
                return moment in anchor.notBefore.time..anchor.notAfter.time
            }
        }
        val candidates = anchors + pool
        for (candidate in candidates) {
            if (candidate.encoded.contentEquals(certificate.encoded)) continue
            if (!isCertificateAuthority(candidate)) continue
            if (moment < candidate.notBefore.time || moment > candidate.notAfter.time) continue
            val limit = candidate.basicConstraints
            if (limit in 0 until Int.MAX_VALUE && caDepth > limit) continue
            if (!verifyWith(certificate, candidate)) continue
            if (anchors.any { it.encoded.contentEquals(candidate.encoded) }) return true
            if (chainToAnchor(candidate, pool, anchors, moment, depth + 1, caDepth + 1)) {
                return true
            }
        }
        return false
    }

    private fun isCertificateAuthority(certificate: X509Certificate): Boolean {
        if (certificate.basicConstraints >= 0) return true
        return runCatching { certificate.keyUsage?.getOrNull(5) == true }.getOrDefault(false)
    }

    private fun verifyWith(certificate: X509Certificate, issuer: X509Certificate): Boolean =
        try {
            certificate.verify(issuer.publicKey)
            true
        } catch (error: Exception) {
            false
        }

    @Volatile
    private var bundledTrust: Pair<List<X509Certificate>, List<X509Certificate>>? = null

    /**
     * Public Indian CCA roots plus the Verasys and SafeScrypt document-signer
     * intermediates. Without them a valid eSign PDF would stay blue forever:
     * Android's system store does not ship the CCA India roots.
     */
    private fun loadBundledTrust(
        context: Context
    ): Pair<List<X509Certificate>, List<X509Certificate>> {
        bundledTrust?.let { return it }
        synchronized(this) {
            bundledTrust?.let { return it }
            val roots = ArrayList<X509Certificate>()
            val intermediates = ArrayList<X509Certificate>()
            val factory = CertificateFactory.getInstance("X.509")
            val names = runCatching { context.assets.list(BUNDLED_TRUST_DIR) }
                .getOrNull()
                ?: emptyArray()
            for (name in names.sorted()) {
                runCatching {
                    context.assets.open("$BUNDLED_TRUST_DIR/$name").use { input ->
                        factory.generateCertificate(input) as X509Certificate
                    }
                }.getOrNull()?.let { certificate ->
                    if (certificate.subjectX500Principal == certificate.issuerX500Principal) {
                        roots.add(certificate)
                    } else {
                        intermediates.add(certificate)
                    }
                }
            }
            val loaded = roots to intermediates
            bundledTrust = loaded
            return loaded
        }
    }

    private fun hashRanges(
        context: Context,
        uri: Uri,
        ranges: LongArray,
        digestName: String
    ): ByteArray {
        val digest = MessageDigest.getInstance(digestName)
        consumeRanges(context, uri, ranges) { buffer, length ->
            digest.update(buffer, 0, length)
        }
        return digest.digest()
    }

    private fun consumeRanges(
        context: Context,
        uri: Uri,
        ranges: LongArray,
        sink: (ByteArray, Int) -> Unit
    ) {
        val input: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("PDF is not readable")
        input.use { stream ->
            var position = 0L
            fun skipTo(target: Long) {
                while (position < target) {
                    val skipped = stream.skip(target - position)
                    if (skipped > 0) {
                        position += skipped
                    } else {
                        if (stream.read() < 0) throw IllegalStateException("Unexpected end of PDF")
                        position++
                    }
                }
            }
            fun consume(offset: Long, length: Long) {
                skipTo(offset)
                var remaining = length
                val buffer = ByteArray(64 * 1024)
                while (remaining > 0) {
                    val wanted = minOf(remaining, buffer.size.toLong()).toInt()
                    val read = stream.read(buffer, 0, wanted)
                    if (read <= 0) throw IllegalStateException("Unexpected end of PDF")
                    sink(buffer, read)
                    position += read
                    remaining -= read
                }
            }
            consume(ranges[0], ranges[1])
            consume(ranges[2], ranges[3])
        }
    }

    private fun digestName(oid: String): String? = when (oid) {
        "1.3.14.3.2.26" -> "SHA-1"
        "2.16.840.1.101.3.4.2.4" -> "SHA-224"
        "2.16.840.1.101.3.4.2.1" -> "SHA-256"
        "2.16.840.1.101.3.4.2.2" -> "SHA-384"
        "2.16.840.1.101.3.4.2.3" -> "SHA-512"
        else -> null
    }

    private fun signatureAlgorithm(signatureOid: String, digestName: String): String? {
        val digest = digestName.replace("-", "")
        return when (signatureOid) {
            "1.2.840.113549.1.1.1" -> "${digest}withRSA"
            "1.2.840.113549.1.1.5" -> "SHA1withRSA"
            "1.2.840.113549.1.1.11" -> "SHA256withRSA"
            "1.2.840.113549.1.1.12" -> "SHA384withRSA"
            "1.2.840.113549.1.1.13" -> "SHA512withRSA"
            "1.2.840.10045.4.1" -> "SHA1withECDSA"
            "1.2.840.10045.4.3.2" -> "SHA256withECDSA"
            "1.2.840.10045.4.3.3" -> "SHA384withECDSA"
            "1.2.840.10045.4.3.4" -> "SHA512withECDSA"
            else -> null
        }
    }

    private fun commonName(principal: X500Principal): String? {
        return try {
            val text = principal.getName(X500Principal.RFC2253)
            val parts = ArrayList<String>()
            val current = StringBuilder()
            var escaped = false
            for (character in text) {
                if (escaped) {
                    current.append(character)
                    escaped = false
                } else when (character) {
                    '\\' -> {
                        current.append(character)
                        escaped = true
                    }
                    ',' -> {
                        parts.add(current.toString())
                        current.setLength(0)
                    }
                    else -> current.append(character)
                }
            }
            parts.add(current.toString())
            for (part in parts) {
                val separator = part.indexOf('=')
                if (separator > 0 &&
                    part.substring(0, separator).trim().equals("CN", ignoreCase = true)
                ) {
                    return part.substring(separator + 1).trim()
                }
            }
            null
        } catch (error: Exception) {
            null
        }
    }

    private fun parsePdfDate(value: String): Long? {
        val match = Regex(
            "D:(\\d{4})(\\d{2})?(\\d{2})?(\\d{2})?(\\d{2})?(\\d{2})?(?:([+-])(\\d{2})'?(\\d{2})'?|Z)?"
        ).find(value.trim()) ?: return null
        val fields = match.groupValues
        val year = fields[1].toIntOrNull() ?: return null
        val calendar = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.clear()
        calendar.set(
            year,
            (fields[2].toIntOrNull() ?: 1) - 1,
            fields[3].toIntOrNull() ?: 1,
            fields[4].toIntOrNull() ?: 0,
            fields[5].toIntOrNull() ?: 0,
            fields[6].toIntOrNull() ?: 0
        )
        var millis = calendar.timeInMillis
        if (fields[7].isNotEmpty()) {
            val sign = if (fields[7] == "-") -1L else 1L
            val offsetMinutes =
                (fields[8].toIntOrNull() ?: 0) * 60L + (fields[9].toIntOrNull() ?: 0)
            millis -= sign * offsetMinutes * 60_000L
        }
        return millis
    }

    private fun formatDate(millis: Long?): String? {
        if (millis == null || millis <= 0L) return null
        return runCatching {
            SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(millis))
        }.getOrNull()
    }

    /* The wording Adobe prints in its signature stamp: the PDF date as written,
       timezone included, rather than the device-local short form. */
    private fun formatAdobeDate(raw: String?, millis: Long?): String? {
        val match = Regex(
            "D:(\\d{4})(\\d{2})?(\\d{2})?(\\d{2})?(\\d{2})?(\\d{2})?(?:([+-])(\\d{2})'?(\\d{2})'?|(Z))?"
        ).find(raw?.trim().orEmpty())
        if (match != null) {
            val fields = match.groupValues
            val offset = if (fields[7].isNotEmpty()) {
                "${fields[7]}${fields[8].padStart(2, '0')}:${fields[9].padStart(2, '0')}"
            } else {
                "+00:00"
            }
            return String.format(Locale.US, "%s.%s.%s %s:%s:%s %s",
                fields[1].padStart(4, '0'),
                fields[2].ifEmpty { "01" }.padStart(2, '0'),
                fields[3].ifEmpty { "01" }.padStart(2, '0'),
                fields[4].ifEmpty { "00" }.padStart(2, '0'),
                fields[5].ifEmpty { "00" }.padStart(2, '0'),
                fields[6].ifEmpty { "00" }.padStart(2, '0'),
                offset
            )
        }
        if (millis == null || millis <= 0L) return null
        return runCatching {
            val text = SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.US).format(Date(millis))
            val total = TimeZone.getDefault().getOffset(millis)
            val sign = if (total >= 0) "+" else "-"
            val absolute = kotlin.math.abs(total)
            val offset = String.format(Locale.US, "%s%02d:%02d", sign, absolute / 3_600_000, (absolute / 60_000) % 60)
            "$text $offset"
        }.getOrNull()
    }

    private class Der(private val data: ByteArray) {

        class El(
            val tag: Int,
            val start: Int,
            val contentStart: Int,
            val contentEnd: Int,
            val end: Int
        ) {
            val constructed: Boolean get() = (tag and 0x20) != 0
        }

        private var position = 0

        fun read(): El {
            val start = position
            require(position + 2 <= data.size) { "Truncated DER" }
            val tag = data[position++].toInt() and 0xff
            var length = data[position++].toInt() and 0xff
            var contentStart = position
            var contentEnd: Int
            var end: Int
            if (length == 0x80) {
                require((tag and 0x20) != 0) { "Indefinite primitive" }
                contentStart = position
                while (true) {
                    require(position + 2 <= data.size) { "Unterminated BER" }
                    if (data[position] == 0.toByte() && data[position + 1] == 0.toByte()) break
                    read()
                }
                contentEnd = position
                end = position + 2
            } else {
                if ((length and 0x80) != 0) {
                    val count = length and 0x7f
                    require(count in 1..4 && position + count <= data.size) { "Invalid DER length" }
                    length = 0
                    repeat(count) {
                        length = (length shl 8) or (data[position++].toInt() and 0xff)
                    }
                }
                contentStart = position
                require(length >= 0 && contentStart + length <= data.size) { "DER overrun" }
                contentEnd = contentStart + length
                end = contentEnd
            }
            position = end
            return El(tag, start, contentStart, contentEnd, end)
        }

        fun children(element: El): List<El> {
            val saved = position
            position = element.contentStart
            val result = ArrayList<El>()
            while (position < element.contentEnd) {
                if (position + 2 <= data.size &&
                    data[position] == 0.toByte() && data[position + 1] == 0.toByte()
                ) {
                    break
                }
                result.add(read())
            }
            position = saved
            return result
        }

        fun raw(element: El): ByteArray = data.copyOfRange(element.start, element.end)

        fun body(element: El): ByteArray =
            data.copyOfRange(element.contentStart, element.contentEnd)

        fun octets(element: El): ByteArray {
            if (!element.constructed) return body(element)
            val output = java.io.ByteArrayOutputStream()
            for (child in children(element)) output.write(octets(child))
            return output.toByteArray()
        }

        fun integer(element: El): BigInteger = BigInteger(1, body(element))

        fun oid(element: El): String {
            val bytes = body(element)
            if (bytes.isEmpty()) return ""
            val first = bytes[0].toInt() and 0xff
            val head = when {
                first < 40 -> 0
                first < 80 -> 1
                else -> 2
            }
            val builder = StringBuilder().append(head).append('.').append(first - head * 40)
            var value = 0L
            for (index in 1 until bytes.size) {
                val byte = bytes[index].toInt() and 0xff
                value = (value shl 7) or (byte and 0x7f).toLong()
                if ((byte and 0x80) == 0) {
                    builder.append('.').append(value)
                    value = 0L
                }
            }
            return builder.toString()
        }

        fun asn1Time(element: El): Long? {
            val value = String(body(element), Charsets.US_ASCII)
            val pattern = when (element.tag) {
                0x17 -> "yyMMddHHmmss'Z'"
                0x18 -> "yyyyMMddHHmmss'Z'"
                else -> return null
            }
            return runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(value)?.time
            }.getOrNull()
        }
    }

    private const val BUNDLED_TRUST_DIR = "pdf-trust"
    private const val OID_SIGNED_DATA = "1.2.840.113549.1.7.2"
    private const val OID_MESSAGE_DIGEST = "1.2.840.113549.1.9.4"
    private const val OID_SIGNING_TIME = "1.2.840.113549.1.9.5"
}
