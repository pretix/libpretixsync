package eu.pretix.libpretixsync.crypto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.util.Base64

class UtilsTest {
    private val publicKey = readPubkeyFromPem(
        String(Base64.getDecoder().decode("LS0tLS1CRUdJTiBQVUJMSUMgS0VZLS0tLS0KTUNvd0JRWURLMlZ3QXlFQTdBRDcvdkZBMzNFc1k0ejJQSHI3aVpQc1o4bjVkaDBhalA4Z3l6Tm1tSXM9Ci0tLS0tRU5EIFBVQkxJQyBLRVktLS0tLQo="))
    )
    private val groupOrder = BigInteger("7237005577332262213973186563042994240857116359379907606001950938285454250989")
    private val validQrcode = "E4BibyTSylQOgeKjuMPiTDxi5HXPuTVsx1qCli3IL0143gj0EZXOB9iQInANxRFJTt4Pf9nXnHdB91Qk/RN0L5AIBABSxw2TKFnSUNUCKAEAPAQA"
    private val sPlusGroupOrderQrcode = "U4BibyTSylQOgeKjuMPiTXiaz/E4lH/hJ/bb/WNGfr043gj0EZXOB9iQInANxRFJTt4Pf9nXnHdB91Qk/RN0L5AIBABSxw2TKFnSUNUCKAEAPAQA"

    private fun decodeSig1(qrcode: String): Pair<ByteArray, ByteArray> {
        val raw = Base64.getDecoder().decode(qrcode.reversed())
        val payloadLength = 5 + ((raw[1].toInt() and 0xFF) shl 8) + (raw[2].toInt() and 0xFF)
        return Pair(raw.copyOfRange(5, payloadLength), raw.copyOfRange(payloadLength, raw.size))
    }

    private fun signatureWithS(s: BigInteger): ByteArray {
        val be = s.toByteArray().takeLast(32).toByteArray()
        val le = ByteArray(32)
        be.reversedArray().copyInto(le)
        return ByteArray(32) + le
    }

    @Test
    fun testValidSignature() {
        val (payload, signature) = decodeSig1(validQrcode)
        assertTrue(isValidSignature(payload, signature, publicKey))
    }

    @Test
    fun testSignatureWithSPlusGroupOrderRejected() {
        val (payload, signature) = decodeSig1(sPlusGroupOrderQrcode)
        assertFalse(isValidSignature(payload, signature, publicKey))
    }

    @Test
    fun testWrongSignatureLengthRejected() {
        val (payload, signature) = decodeSig1(validQrcode)
        assertFalse(isValidSignature(payload, signature.copyOfRange(0, 63), publicKey))
        assertFalse(isValidSignature(payload, signature + byteArrayOf(0), publicKey))
    }

    @Test
    fun testCanonicalSignatureBoundaries() {
        assertTrue(isCanonicalSignature(signatureWithS(BigInteger.ZERO)))
        assertTrue(isCanonicalSignature(signatureWithS(groupOrder.subtract(BigInteger.ONE))))
        assertFalse(isCanonicalSignature(signatureWithS(groupOrder)))
        assertFalse(isCanonicalSignature(signatureWithS(BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE))))
        assertFalse(isCanonicalSignature(ByteArray(63)))
        assertFalse(isCanonicalSignature(ByteArray(65)))
    }
}
