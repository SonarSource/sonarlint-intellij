/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonarlint.intellij.core.cfamily

import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.io.HttpRequests
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.Security
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPCompressedData
import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPSignatureList
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider
import org.sonarlint.intellij.SonarLintPlugin
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.util.GlobalLogOutput
import org.sonarsource.sonarlint.core.client.utils.ClientLogOutput

/**
 * Service to manage CFamily analyzer availability, download, and verification.
 * The analyzer signature is included during build via copyAsc in build.gradle.kts.
 * The analyzer JAR is downloaded at runtime if missing and verified against the signature using BouncyCastle.
 */
@Service(Service.Level.APP)
class CFamilyAnalyzerManager {

    private val checkInProgress = AtomicBoolean(false)
    private val checkFuture = AtomicReference<CompletableFuture<CheckResult>?>()
    private val analyzerReady = AtomicBoolean(false)

    companion object {
        private const val CFAMILY_PLUGIN_PATTERN = "sonar-cfamily-plugin-*.jar"
        private const val CFAMILY_SIGNATURE_PATTERN = "sonar-cfamily-plugin-*.jar.asc"
        private const val SONAR_PUBLIC_KEY = "sonarsource-public.key"
        private const val CFAMILY_DOWNLOAD_URL_TEMPLATE =
            "https://binaries.sonarsource.com/CommercialDistribution/sonar-cfamily-plugin/sonar-cfamily-plugin-%s.jar"
        private val VERSION_REGEX = Regex("sonar-cfamily-plugin-(.*)\\.jar\\.asc")
    }

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    fun ensureAnalyzerAvailable(progressIndicator: ProgressIndicator?): CompletableFuture<CheckResult> {
        val existingFuture = checkFuture.get()
        if (existingFuture != null && !existingFuture.isCompletedExceptionally) {
            return existingFuture
        }

        val future = CompletableFuture<CheckResult>()
        if (checkFuture.compareAndSet(null, future) || checkInProgress.compareAndSet(false, true)) {
            try {
                val result = performCheck(progressIndicator)
                future.complete(result)
                analyzerReady.set(result is CheckResult.Available)
            } catch (e: Exception) {
                getService(GlobalLogOutput::class.java).logError("Error checking CFamily analyzer", e)
                future.completeExceptionally(e)
            } finally {
                checkInProgress.set(false)
            }
            return future
        }

        return checkFuture.get() ?: future
    }

    private fun performCheck(progressIndicator: ProgressIndicator?): CheckResult {
        progressIndicator?.text = "Checking CFamily analyzer..."

        val pluginsDir = getPluginsDir()
        val analyzerPath = findCFamilyAnalyzer(pluginsDir)

        return when {
            analyzerPath != null -> {
                getService(GlobalLogOutput::class.java).log(
                    "Found CFamily analyzer at: ${analyzerPath.fileName}",
                    ClientLogOutput.Level.INFO
                )

                val signatureResult = verifySignature(analyzerPath, progressIndicator)
                signatureResult
            }

            else -> {
                getService(GlobalLogOutput::class.java).log(
                    "CFamily analyzer not found, attempting download...",
                    ClientLogOutput.Level.INFO
                )
                downloadAnalyzer(pluginsDir, progressIndicator)
            }
        }
    }

    private fun findCFamilyAnalyzer(pluginsDir: Path): Path? {
        if (!Files.isDirectory(pluginsDir)) {
            return null
        }

        return Files.newDirectoryStream(pluginsDir, CFAMILY_PLUGIN_PATTERN).use { stream ->
            stream.firstOrNull()
        }
    }

    private fun verifySignature(analyzerPath: Path, progressIndicator: ProgressIndicator?): CheckResult {
        progressIndicator?.text = "Verifying CFamily analyzer signature..."

        try {
            val signatureFile = analyzerPath.parent.resolve("${analyzerPath.fileName}.asc")
            if (!Files.exists(signatureFile)) {
                getService(GlobalLogOutput::class.java).log(
                    "Signature file not found for ${analyzerPath.fileName}",
                    ClientLogOutput.Level.WARN
                )
                return CheckResult.Available(analyzerPath) // Analyzer exists but no signature
            }

            val keyRing = loadPublicKeyRing()
            if (keyRing == null) {
                getService(GlobalLogOutput::class.java).log(
                    "Could not load SonarSource public key ring",
                    ClientLogOutput.Level.WARN
                )
                return CheckResult.InvalidSignature(analyzerPath)
            }

            val isValid = verifyPgpSignature(analyzerPath, signatureFile, keyRing)
            if (isValid) {
                getService(GlobalLogOutput::class.java).log(
                    "CFamily analyzer signature verified successfully",
                    ClientLogOutput.Level.INFO
                )
                return CheckResult.Available(analyzerPath)
            } else {
                getService(GlobalLogOutput::class.java).log(
                    "CFamily analyzer signature verification failed",
                    ClientLogOutput.Level.ERROR
                )
                return CheckResult.InvalidSignature(analyzerPath)
            }
        } catch (e: Exception) {
            getService(GlobalLogOutput::class.java).logError("Error verifying signature", e)
            return CheckResult.InvalidSignature(analyzerPath)
        }
    }

    private fun loadPublicKeyRing(): PGPPublicKeyRingCollection? {
        return try {
            val keyStream = javaClass.classLoader.getResourceAsStream(SONAR_PUBLIC_KEY)
                ?: throw FileNotFoundException("PGP key not found in resources: $SONAR_PUBLIC_KEY")

            keyStream.use { keyIn ->
                val decoder = PGPUtil.getDecoderStream(BufferedInputStream(keyIn))
                PGPPublicKeyRingCollection(decoder, JcaKeyFingerprintCalculator())
            }
        } catch (e: Exception) {
            getService(GlobalLogOutput::class.java).logError("Error loading public key ring", e)
            null
        }
    }

    private fun verifyPgpSignature(dataFile: Path, signatureFile: Path, keyRing: PGPPublicKeyRingCollection): Boolean {
        try {
            FileInputStream(signatureFile.toFile()).use { sigIn ->
                val decoderStream = PGPUtil.getDecoderStream(BufferedInputStream(sigIn))
                val pgpFact = PGPObjectFactory(decoderStream, JcaKeyFingerprintCalculator())

                val signatureList: PGPSignatureList = when (val obj = pgpFact.nextObject()) {
                    is PGPCompressedData -> {
                        val pgpFact2 = PGPObjectFactory(obj.dataStream, JcaKeyFingerprintCalculator())
                        pgpFact2.nextObject() as PGPSignatureList
                    }

                    is PGPSignatureList -> obj
                    else -> return false
                }

                if (signatureList.isEmpty) {
                    return false
                }

                val signature = signatureList[0]

                val publicKey = keyRing.getPublicKey(signature.keyID)
                if (publicKey == null) {
                    getService(GlobalLogOutput::class.java).log(
                        "Public key not found for signature keyID=${signature.keyID}",
                        ClientLogOutput.Level.ERROR
                    )
                    return false
                }

                signature.init(JcaPGPContentVerifierBuilderProvider().setProvider("BC"), publicKey)

                FileInputStream(dataFile.toFile()).use { dataIn ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (dataIn.read(buffer).also { bytesRead = it } != -1) {
                        signature.update(buffer, 0, bytesRead)
                    }
                }

                return signature.verify()
            }
        } catch (e: Exception) {
            getService(GlobalLogOutput::class.java).logError("Error verifying PGP signature", e)
            return false
        }
    }


    private fun getVersionFromSignature(pluginsDir: Path): String? {
        if (!Files.isDirectory(pluginsDir)) {
            return null
        }

        return Files.newDirectoryStream(pluginsDir, CFAMILY_SIGNATURE_PATTERN).use { stream ->
            val signatureFile = stream.firstOrNull()
            if (signatureFile != null) {
                val match = VERSION_REGEX.matchEntire(signatureFile.fileName.toString())
                match?.groupValues?.get(1)
            } else {
                null
            }
        }
    }

    private fun downloadAnalyzer(pluginsDir: Path, progressIndicator: ProgressIndicator?): CheckResult {
        progressIndicator?.text = "Downloading CFamily analyzer..."
        progressIndicator?.isIndeterminate = false

        val version = getVersionFromSignature(pluginsDir)
        if (version == null) {
            getService(GlobalLogOutput::class.java).log(
                "Cannot determine CFamily version - signature file not found",
                ClientLogOutput.Level.ERROR
            )
            return CheckResult.DownloadFailed("Signature file not found")
        }

        getService(GlobalLogOutput::class.java).log(
            "Downloading CFamily analyzer version: $version",
            ClientLogOutput.Level.INFO
        )

        try {
            Files.createDirectories(pluginsDir)
            val downloadUrl = String.format(CFAMILY_DOWNLOAD_URL_TEMPLATE, version)
            val targetFile = pluginsDir.resolve("sonar-cfamily-plugin-$version.jar")
            val tempFile = Files.createTempFile(pluginsDir, "cfamily-download-", ".jar")

            try {
                HttpRequests.request(downloadUrl)
                    .connectTimeout(30000)
                    .readTimeout(30000)
                    .saveToFile(tempFile.toFile(), progressIndicator)

                Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING)

                getService(GlobalLogOutput::class.java).log(
                    "CFamily analyzer downloaded successfully to: ${targetFile.fileName}",
                    ClientLogOutput.Level.INFO
                )

                val verificationResult = verifySignature(targetFile, progressIndicator)
                return when (verificationResult) {
                    is CheckResult.Available -> CheckResult.Downloaded(targetFile)
                    else -> verificationResult
                }
            } catch (e: ProcessCanceledException) {
                Files.deleteIfExists(tempFile)
                getService(GlobalLogOutput::class.java).log(
                    "CFamily analyzer download cancelled",
                    ClientLogOutput.Level.INFO
                )
                return CheckResult.Cancelled
            } catch (e: IOException) {
                Files.deleteIfExists(tempFile)
                getService(GlobalLogOutput::class.java).logError("Error downloading CFamily analyzer", e)
                return CheckResult.DownloadFailed(e.message ?: "Unknown error")
            }
        } catch (e: Exception) {
            getService(GlobalLogOutput::class.java).logError("Error preparing download", e)
            return CheckResult.DownloadFailed(e.message ?: "Unknown error")
        }
    }

    private fun getPluginsDir(): Path {
        val plugin = getService(SonarLintPlugin::class.java)
        return plugin.path.resolve("plugins")
    }

    fun isAnalyzerReady(): Boolean {
        return analyzerReady.get()
    }

    /**
     * Result of checking/downloading CFamily analyzer
     */
    sealed class CheckResult {
        data class Available(val path: Path) : CheckResult()
        data class Downloaded(val path: Path) : CheckResult()
        data class InvalidSignature(val path: Path) : CheckResult()
        data class DownloadFailed(val reason: String) : CheckResult()
        data object MissingAndDownloadDisabled : CheckResult()
        data object Cancelled : CheckResult()
    }
}
