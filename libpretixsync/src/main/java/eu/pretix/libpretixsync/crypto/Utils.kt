package eu.pretix.libpretixsync.crypto


import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import org.apache.commons.codec.binary.Base64.decodeBase64

fun readPubkeyFromPem(pem: String): EdDSAPublicKey {
    val keySpecs = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
    var pubKeyPEM: String = pem.replace("-----BEGIN PUBLIC KEY-----\n", "")
    pubKeyPEM = pubKeyPEM.replace("-----END PUBLIC KEY-----", "")
    val asn1Bytes = decodeBase64(pubKeyPEM.trim())
    // asn1Bytes is in ASN.1 format and contains the structure SubjectPublicKeyInfo defined in
    // RFC 3280. However, since we know what algorithm is used, let's take a very stupid approach
    // to parsing, for now.
    val keyBytes = asn1Bytes.sliceArray(12..43)
    val keySpec = EdDSAPublicKeySpec(keyBytes, keySpecs)
    return EdDSAPublicKey(keySpec)
}

fun isValidSignature(payload: ByteArray, signature: ByteArray, publicKey: EdDSAPublicKey): Boolean {
    val engine = EdDSAEngine()
    engine.initVerify(publicKey)
    return engine.verifyOneShot(payload, signature)
}
