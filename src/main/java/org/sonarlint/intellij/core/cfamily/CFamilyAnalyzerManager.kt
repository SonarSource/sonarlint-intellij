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
 * 
 * Storage:
 * - Downloaded analyzers are stored in {plugin.path}/analyzer-cache/cfamily/
 * - Signature file is bundled in {plugin.path}/plugins/ during build (via copyAsc in build.gradle.kts)
 * 
 * Version determination:
 * - The analyzer version is determined from build metadata (cfamily-version.properties)
 * 
 * Connected Mode integration:
 * - EnabledLanguages.getEmbeddedPluginsForConnectedMode() checks the cache directory first
 * - Falls back to the bundled plugins directory if not found in cache
 * 
 * This service supports:
 * - Detailed progress reporting through ProgressIndicator with text updates
 * - Cancellation via ProgressIndicator.isCanceled at strategic checkpoints
 * - Automatic cleanup of temporary files on cancellation
 * - PGP signature verification using BouncyCastle
 */
@Service(Service.Level.APP)
class CFamilyAnalyzerManager {

    private val checkInProgress = AtomicBoolean(false)
    private val checkFuture = AtomicReference<CompletableFuture<CheckResult>?>()
    private val analyzerReady = AtomicBoolean(false)

    companion object {
        private const val CFAMILY_PLUGIN_PATTERN = "sonar-cfamily-plugin-*.jar"
        private const val SONAR_PUBLIC_KEY = "sonarsource-public.key"
        private const val CFAMILY_VERSION_PROPERTIES = "cfamily-version.properties"
        private const val CFAMILY_DOWNLOAD_URL_TEMPLATE =
            "https://binaries.sonarsource.com/CommercialDistribution/sonar-cfamily-plugin/sonar-cfamily-plugin-%s.jar"
        private const val ANALYZER_CACHE_DIR = "analyzer-cache"
        private const val CFAMILY_CACHE_DIR = "cfamily"
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
            } catch (e: ProcessCanceledException) {
                getService(GlobalLogOutput::class.java).log(
                    "CFamily analyzer check was cancelled",
                    ClientLogOutput.Level.INFO
                )
                future.complete(CheckResult.Cancelled)
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
        checkCancellation(progressIndicator)

        val cacheDir = getCFamilyCacheDir()
        val analyzerPath = findCFamilyAnalyzer(cacheDir)
        checkCancellation(progressIndicator)

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
                    "CFamily analyzer not found in cache, attempting download...",
                    ClientLogOutput.Level.INFO
                )
                downloadAnalyzer(cacheDir, progressIndicator)
            }
        }
    }

    private fun findCFamilyAnalyzer(cacheDir: Path): Path? {
        if (!Files.isDirectory(cacheDir)) {
            return null
        }

        return Files.newDirectoryStream(cacheDir, CFAMILY_PLUGIN_PATTERN).use { stream ->
            stream.firstOrNull()
        }
    }

    private fun verifySignature(analyzerPath: Path, progressIndicator: ProgressIndicator?): CheckResult {
        progressIndicator?.text = "Verifying CFamily analyzer signature..."
        checkCancellation(progressIndicator)

        try {
            // Signature file is bundled in the plugins directory, not the cache
            val signatureFile = findBundledSignatureFile()
            if (signatureFile == null || !Files.exists(signatureFile)) {
                getService(GlobalLogOutput::class.java).log(
                    "Bundled signature file not found for CFamily analyzer",
                    ClientLogOutput.Level.WARN
                )
                return CheckResult.Available(analyzerPath) // Analyzer exists but no signature
            }

            checkCancellation(progressIndicator)
            val keyRing = loadPublicKeyRing()
            if (keyRing == null) {
                getService(GlobalLogOutput::class.java).log(
                    "Could not load SonarSource public key ring",
                    ClientLogOutput.Level.WARN
                )
                return CheckResult.InvalidSignature(analyzerPath)
            }

            progressIndicator?.text = "Verifying signature cryptographically..."
            checkCancellation(progressIndicator)
            
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


    private fun getCFamilyVersion(): String? {
        return try {
            val propertiesStream = javaClass.classLoader.getResourceAsStream(CFAMILY_VERSION_PROPERTIES)
                ?: throw FileNotFoundException("CFamily version properties not found: $CFAMILY_VERSION_PROPERTIES")

            propertiesStream.use { input ->
                val properties = java.util.Properties()
                properties.load(input)
                properties.getProperty("cfamily.version")
            }
        } catch (e: Exception) {
            getService(GlobalLogOutput::class.java).logError("Error loading CFamily version from properties", e)
            null
        }
    }

    private fun downloadAnalyzer(cacheDir: Path, progressIndicator: ProgressIndicator?): CheckResult {
        progressIndicator?.text = "Preparing to download CFamily analyzer..."
        progressIndicator?.isIndeterminate = false
        checkCancellation(progressIndicator)

        val version = getCFamilyVersion()
        if (version == null) {
            getService(GlobalLogOutput::class.java).log(
                "Cannot determine CFamily version from build metadata",
                ClientLogOutput.Level.ERROR
            )
            return CheckResult.DownloadFailed("Version metadata not found")
        }

        getService(GlobalLogOutput::class.java).log(
            "Downloading CFamily analyzer version: $version to cache",
            ClientLogOutput.Level.INFO
        )

        try {
            Files.createDirectories(cacheDir)
            val downloadUrl = String.format(CFAMILY_DOWNLOAD_URL_TEMPLATE, version)
            val targetFile = cacheDir.resolve("sonar-cfamily-plugin-$version.jar")
            val tempFile = Files.createTempFile(cacheDir, "cfamily-download-", ".jar")

            try {
                // Download the JAR file
                progressIndicator?.text = "Downloading CFamily analyzer JAR ($version)..."
                checkCancellation(progressIndicator)
                
                HttpRequests.request(downloadUrl)
                    .connectTimeout(30000)
                    .readTimeout(30000)
                    .saveToFile(tempFile.toFile(), progressIndicator)
                
                checkCancellation(progressIndicator)

                Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING)

                getService(GlobalLogOutput::class.java).log(
                    "CFamily analyzer downloaded successfully to cache: ${targetFile.fileName}",
                    ClientLogOutput.Level.INFO
                )

                progressIndicator?.text = "Verifying downloaded analyzer..."
                checkCancellation(progressIndicator)
                
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

    private fun getCFamilyCacheDir(): Path {
        val plugin = getService(SonarLintPlugin::class.java)
        return plugin.path.resolve(ANALYZER_CACHE_DIR).resolve(CFAMILY_CACHE_DIR)
    }

    fun getCachedAnalyzerPath(): Path? {
        return findCFamilyAnalyzer(getCFamilyCacheDir())
    }

    private fun getPluginsDir(): Path {
        val plugin = getService(SonarLintPlugin::class.java)
        return plugin.path.resolve("plugins")
    }

    private fun findBundledSignatureFile(): Path? {
        val pluginsDir = getPluginsDir()
        if (!Files.isDirectory(pluginsDir)) {
            return null
        }
        
        // Find the signature file matching the pattern sonar-cfamily-plugin-*.jar.asc
        return Files.newDirectoryStream(pluginsDir, "sonar-cfamily-plugin-*.jar.asc").use { stream ->
            stream.firstOrNull()
        }
    }

    fun isAnalyzerReady(): Boolean {
        return analyzerReady.get()
    }

    private fun checkCancellation(progressIndicator: ProgressIndicator?) {
        if (progressIndicator?.isCanceled == true) {
            throw ProcessCanceledException()
        }
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
