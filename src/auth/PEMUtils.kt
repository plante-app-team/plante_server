package vegancheckteam.plante_server.auth

//Copyright 2017 - https://github.com/lbalmaceda
//Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

import java.io.File
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.IOException
import java.security.KeyFactory
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.EncodedKeySpec
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemReader

/**
 * Class is taked from https://gist.github.com/lbalmaceda/9a0c7890c2965826c04119dcfb1a5469
 */
object PemUtils {
    private fun parsePEMFile(pemFile: File): ByteArray {
        if (!pemFile.isFile || !pemFile.exists()) {
            throw FileNotFoundException(String.format("The file '${pemFile.absolutePath}' doesn't exist."))
        }
        val reader = PemReader(FileReader(pemFile))
        reader.use {
            val pemObject = reader.readPemObject()
            return pemObject.content
        }
    }

    private fun getPublicKey(keyBytes: ByteArray, algorithm: String): PublicKey? {
        val kf = KeyFactory.getInstance(algorithm)
        val keySpec: EncodedKeySpec = X509EncodedKeySpec(keyBytes)
        return kf.generatePublic(keySpec)
    }

    private fun getPrivateKey(keyBytes: ByteArray, algorithm: String): PrivateKey? {
        val kf = KeyFactory.getInstance(algorithm)
        val keySpec: EncodedKeySpec = PKCS8EncodedKeySpec(keyBytes)
        return kf.generatePrivate(keySpec)
    }

    fun readPublicKeyFromFile(filepath: String, algorithm: String): PublicKey? {
        val bytes = parsePEMFile(File(filepath))
        return getPublicKey(bytes, algorithm)
    }

    fun readPrivateKeyFromFile(filepath: String, algorithm: String): PrivateKey? {
        val bytes = parsePEMFile(File(filepath))
        return getPrivateKey(bytes, algorithm)
    }
}
