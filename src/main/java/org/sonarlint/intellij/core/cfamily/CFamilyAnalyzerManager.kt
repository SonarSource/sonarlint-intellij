/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
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
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import org.sonarlint.intellij.core.analyzer.AnalyzerCacheManager
import org.sonarlint.intellij.util.GlobalLogOutput
import org.sonarlint.intellij.util.runOnPooledThread
import org.sonarsource.sonarlint.core.client.utils.ClientLogOutput

/**
 * Service to manage CFamily analyzer availability and verification.
 * Downloads are delegated to CFamilyAnalyzerDownloader service.
 *
 * Storage:
 * - Downloaded analyzers are stored in {plugin.path}/analyzer-cache/cfamily/
 * - Signature file is bundled in {plugin.path}/plugins/ during build (via copyAsc in build.gradle.kts)
 *
 * Version determination:
 * - The analyzer version is determined from build metadata (cfamily-version.properties)
 *
 * User preferences:
 * - Users can disable automatic download via Settings > Tools > SonarQube > General
 * - When disabled, returns CheckResult.MissingAndDownloadDisabled
 * - Re-enabling the setting allows download on next check
 *
 * This service supports:
 * - Detailed progress reporting through ProgressIndicator with text updates
 * - Cancellation via ProgressIndicator.isCanceled checks (returns CheckResult.Cancelled)
 * - PGP signature verification using BouncyCastle
 */
@Service(Service.Level.APP)
class CFamilyAnalyzerManager {

    private val checkFuture = AtomicReference<CompletableFuture<CheckResult>?>()
    private val analyzerReady = AtomicBoolean(false)
    private val cacheManager = getService(AnalyzerCacheManager::class.java)
    private val bouncyCastleProvider = BouncyCastleProvider()

    companion object {
        private const val CFAMILY_PLUGIN_PATTERN = "sonar-cfamily-plugin-*.jar"
        private const val SONAR_PUBLIC_KEY = "sonarsource-public.key"
        private const val CFAMILY_VERSION_PROPERTIES = "cfamily-version.properties"
        private const val ANALYZER_CACHE_DIR = "analyzer-cache"
        private const val CFAMILY_CACHE_DIR = "cfamily"
    }

    init {
        // Start async cleanup task at service initialization (skip in unit tests to avoid thread leaks)
        if (!ApplicationManager.getApplication().isUnitTestMode) {
            cacheManager.startCleanupTask()
        }
    }

    fun ensureAnalyzerAvailable(progressIndicator: ProgressIndicator?): CompletableFuture<CheckResult> {
        var attempts = 0
        while (true) {
            if (attempts++ > 100) {
                val error = IllegalStateException("CFamily analyzer check future CAS loop did not stabilize after 100 attempts")
                getService(GlobalLogOutput::class.java).logError("CFamily analyzer check CAS loop exceeded max attempts", error)
                val failed = CompletableFuture<CheckResult>()
                failed.completeExceptionally(error)
                return failed
            }

            val current = checkFuture.get()

            if (current != null) {
                if (!current.isDone || (!current.isCompletedExceptionally && !current.isCancelled)) {
                    return current
                }

                if (!checkFuture.compareAndSet(current, null)) {
                    continue
                }
            }

            val newFuture = CompletableFuture<CheckResult>()
            if (checkFuture.compareAndSet(null, newFuture)) {
                // Run the check asynchronously on a pooled thread so waitForFuture can poll and cancel
                runOnPooledThread {
                    try {
                        // Don't use ProgressManager.runProcess to avoid conflicts when already under a progress indicator
                        // The indicator is still passed to performCheck for cancellation checks and to child operations
                        val result = performCheck(progressIndicator)
                        newFuture.complete(result)
                        analyzerReady.set(result is CheckResult.Available)
                    } catch (e: ProcessCanceledException) {
                        // User cancelled - complete with Cancelled result, don't log as error
                        newFuture.complete(CheckResult.Cancelled)
                    } catch (e: Exception) {
                        getService(GlobalLogOutput::class.java).logError("Error checking CFamily analyzer", e)
                        newFuture.completeExceptionally(e)
                    }
                }
                return newFuture
            }
        }
    }

    private fun performCheck(progressIndicator: ProgressIndicator?): CheckResult {
        progressIndicator?.text = "Checking CFamily analyzer..."

        val cacheDir = getCFamilyCacheDir()
        val analyzerPath = findCFamilyAnalyzer(cacheDir)

        return when {
            analyzerPath != null -> {
                getService(GlobalLogOutput::class.java).log(
                    "Found CFamily analyzer at: ${analyzerPath.fileName}",
                    ClientLogOutput.Level.INFO
                )

                // Update access timestamp
                cacheManager.updateAnalyzerTimestamp(analyzerPath)

                val signatureResult = verifySignature(analyzerPath, progressIndicator)
                signatureResult
            }

            else -> {
                // Check if user has disabled CFamily downloads
                if (getGlobalSettings().isNeverDownloadCFamilyAnalyzer) {
                    getService(GlobalLogOutput::class.java).log(
                        "CFamily analyzer not found and automatic download is disabled. " +
                            "C/C++ analysis will not be available. " +
                            "To enable, go to Settings > Tools > SonarQube > General and uncheck 'Never download CFamily analyzer'",
                        ClientLogOutput.Level.WARN
                    )
                    return CheckResult.MissingAndDownloadDisabled
                }

                getService(GlobalLogOutput::class.java).log(
                    "CFamily analyzer not found in cache, attempting download...",
                    ClientLogOutput.Level.INFO
                )
                downloadAndVerifyAnalyzer(progressIndicator)
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

            if (progressIndicator?.isCanceled == true) {
                return CheckResult.Cancelled
            }

            val keyRing = loadPublicKeyRing()
            if (keyRing == null) {
                getService(GlobalLogOutput::class.java).log(
                    "Could not load SonarSource public key ring",
                    ClientLogOutput.Level.WARN
                )
                return CheckResult.InvalidSignature(analyzerPath)
            }

            if (progressIndicator?.isCanceled == true) {
                return CheckResult.Cancelled
            }

            progressIndicator?.text = "Verifying signature..."

            val isValid = verifyPgpSignature(analyzerPath, signatureFile, keyRing)
            val result = if (isValid) {
                getService(GlobalLogOutput::class.java).log(
                    "CFamily analyzer signature verified successfully",
                    ClientLogOutput.Level.INFO
                )
                cacheManager.updateAnalyzerTimestamp(analyzerPath)
                CheckResult.Available(analyzerPath)
            } else {
                getService(GlobalLogOutput::class.java).log(
                    "CFamily analyzer signature verification failed",
                    ClientLogOutput.Level.ERROR
                )
                CheckResult.InvalidSignature(analyzerPath)
            }
            return result

        } catch (e: ProcessCanceledException) {
            // Re-throw cancellation instead of treating as verification failure
            throw e
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

                signature.init(JcaPGPContentVerifierBuilderProvider().setProvider(bouncyCastleProvider), publicKey)

                FileInputStream(dataFile.toFile()).use { dataIn ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (dataIn.read(buffer).also { bytesRead = it } != -1) {
                        // Check for cancellation during signature verification (can take seconds for large files)
                        ProgressManager.checkCanceled()
                        signature.update(buffer, 0, bytesRead)
                    }
                }

                return signature.verify()
            }
        } catch (e: ProcessCanceledException) {
            // Re-throw cancellation exception instead of treating it as verification failure
            throw e
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

    private fun downloadAndVerifyAnalyzer(progressIndicator: ProgressIndicator?): CheckResult {
        val version = getCFamilyVersion()
        if (version == null) {
            getService(GlobalLogOutput::class.java).log(
                "Cannot determine CFamily version from build metadata",
                ClientLogOutput.Level.ERROR
            )
            return CheckResult.DownloadFailed("Version metadata not found")
        }

        val downloader = getService(CFamilyAnalyzerDownloader::class.java)
        val downloadResult = downloader.downloadAnalyzer(version, progressIndicator)

        return when (downloadResult) {
            is CFamilyAnalyzerDownloader.DownloadResult.Success -> {
                if (progressIndicator?.isCanceled == true) {
                    return CheckResult.Cancelled
                }

                progressIndicator?.text = "Verifying downloaded analyzer..."

                val verificationResult = verifySignature(downloadResult.path, progressIndicator)
                when (verificationResult) {
                    is CheckResult.Available -> CheckResult.Downloaded(downloadResult.path)
                    else -> verificationResult
                }
            }

            is CFamilyAnalyzerDownloader.DownloadResult.Failed -> {
                CheckResult.DownloadFailed(downloadResult.reason)
            }

            CFamilyAnalyzerDownloader.DownloadResult.Cancelled -> {
                CheckResult.Cancelled
            }
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
