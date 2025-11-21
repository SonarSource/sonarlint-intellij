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
import java.util.Properties
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
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
 * Storage:
 * - Downloaded analyzers are stored in {plugin.path}/analyzer-cache/cfamily/
 * - Signature file is bundled in {plugin.path}/plugins/ during build (via copyAsc in build.gradle.kts)
 *
 * Version determination:
 * - The analyzer version is determined from build metadata (cfamily-version.properties)
 */
@Service(Service.Level.APP)
class CFamilyAnalyzerManager {

    private val analyzerReady = AtomicBoolean(false)
    private val cacheManager = getService(AnalyzerCacheManager::class.java)
    private val bouncyCastleProvider = BouncyCastleProvider()

    companion object {
        private const val CFAMILY_PLUGIN_PATTERN = "sonar-cfamily-plugin-*.jar"
        private const val SONAR_PUBLIC_KEY = "sonarsource-public.key"
        private const val CFAMILY_VERSION_PROPERTIES = "cfamily-version.properties"
        private const val CFAMILY_VERSION = "cfamily.version"
    }

    init {
        // Start async cleanup task at service initialization (skip in unit tests to avoid thread leaks)
        if (!ApplicationManager.getApplication().isUnitTestMode) {
            cacheManager.startCleanupTask()
        }
    }

    // This function is only meant to be called at the startup activity, if you intend to use it elsewhere,
    // consider implementing a compare and set logic since multiple calls may lead to multiple downloads.
    fun ensureAnalyzerAvailable(progressIndicator: ProgressIndicator): CompletableFuture<CheckResult> {
        val future = CompletableFuture<CheckResult>()
        startAsyncCheck(future, progressIndicator)
        return future
    }

    private fun startAsyncCheck(future: CompletableFuture<CheckResult>, progressIndicator: ProgressIndicator) {
        runOnPooledThread {
            try {
                // The indicator is passed to performCheck for cancellation checks and to child operations
                val result = performCheck(progressIndicator)
                future.complete(result)
                analyzerReady.set(result is CheckResult.Available)
            } catch (e: ProcessCanceledException) {
                // User cancelled - complete with Cancelled result, don't log as error
                future.complete(CheckResult.Cancelled)
                throw e
            } catch (e: Exception) {
                getService(GlobalLogOutput::class.java).logError("Error checking CFamily analyzer", e)
                future.completeExceptionally(e)
            }
        }
    }

    private fun performCheck(progressIndicator: ProgressIndicator): CheckResult {
        progressIndicator.text = "Checking CFamily analyzer..."

        val cacheDir = cacheManager.getCFamilyCacheDir()
        val analyzerPath = findCFamilyAnalyzer(cacheDir)

        return when {
            analyzerPath != null -> {
                getService(GlobalLogOutput::class.java).log(
                    "Found CFamily analyzer at: ${analyzerPath.fileName}",
                    ClientLogOutput.Level.INFO
                )

                // Update access timestamp
                cacheManager.updateAnalyzerTimestamp(analyzerPath)

                verifySignature(analyzerPath, progressIndicator)
            }

            else -> {
                // Check if user has disabled CFamily downloads
                if (getGlobalSettings().isNeverDownloadCFamilyAnalyzer) {
                    getService(GlobalLogOutput::class.java).log(
                        "CFamily analyzer not found and automatic download is disabled. " +
                            "C/C++ analysis will not be available. " +
                            "To enable, go to Settings > Tools > SonarQube and uncheck 'Never download CFamily analyzer'",
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

        val expectedVersion = getCFamilyVersion() ?: return null

        val expectedFileName = "sonar-cfamily-plugin-$expectedVersion.jar"
        val expectedPath = cacheDir.resolve(expectedFileName)

        return if (Files.exists(expectedPath)) {
            expectedPath
        } else {
            getService(GlobalLogOutput::class.java).log(
                "Expected CFamily analyzer version $expectedVersion not found at: $expectedFileName",
                ClientLogOutput.Level.DEBUG
            )
            null
        }
    }

    private fun verifySignature(analyzerPath: Path, progressIndicator: ProgressIndicator): CheckResult {
        progressIndicator.text = "Verifying CFamily analyzer signature..."

        try {
            val signatureFile = findBundledSignatureFile()
            if (signatureFile == null || !Files.exists(signatureFile)) {
                getService(GlobalLogOutput::class.java).log("Bundled signature file not found for CFamily analyzer", ClientLogOutput.Level.WARN)
                return CheckResult.Available(analyzerPath)
            }

            if (progressIndicator.isCanceled == true) {
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

            if (progressIndicator.isCanceled == true) {
                return CheckResult.Cancelled
            }

            progressIndicator.text = "Verifying signature..."

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

                val signatureList = when (val obj = pgpFact.nextObject()) {
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
                val properties = Properties()
                properties.load(input)
                properties.getProperty(CFAMILY_VERSION)
            }
        } catch (e: Exception) {
            getService(GlobalLogOutput::class.java).logError("Error loading CFamily version from properties", e)
            null
        }
    }

    private fun downloadAndVerifyAnalyzer(progressIndicator: ProgressIndicator): CheckResult {
        val version = getCFamilyVersion()
        if (version == null) {
            getService(GlobalLogOutput::class.java).log(
                "Cannot determine CFamily version from build metadata",
                ClientLogOutput.Level.ERROR
            )
            return CheckResult.DownloadFailed("Version metadata not found")
        }

        val downloader = getService(CFamilyAnalyzerDownloader::class.java)

        return when (val downloadResult = downloader.downloadAnalyzer(version, progressIndicator)) {
            is CFamilyAnalyzerDownloader.DownloadResult.Success -> {
                if (progressIndicator.isCanceled == true) {
                    return CheckResult.Cancelled
                }

                progressIndicator.text = "Verifying downloaded analyzer..."

                when (val verificationResult = verifySignature(downloadResult.path, progressIndicator)) {
                    is CheckResult.Available -> CheckResult.Downloaded(downloadResult.path)
                    else -> verificationResult
                }
            }

            is CFamilyAnalyzerDownloader.DownloadResult.Failed -> {
                CheckResult.DownloadFailed(downloadResult.reason)
            }
        }
    }

    fun getCachedAnalyzerPath(): Path? {
        return findCFamilyAnalyzer(cacheManager.getCFamilyCacheDir())
    }

    private fun findBundledSignatureFile(): Path? {
        val pluginsDir = getService(SonarLintPlugin::class.java).path.resolve("plugins")
        if (!Files.isDirectory(pluginsDir)) {
            return null
        }

        val expectedVersion = getCFamilyVersion() ?: return null

        val expectedFileName = "sonar-cfamily-plugin-$expectedVersion.jar.asc"
        val expectedPath = pluginsDir.resolve(expectedFileName)

        return if (Files.exists(expectedPath)) {
            expectedPath
        } else {
            getService(GlobalLogOutput::class.java).log(
                "Expected CFamily signature file version $expectedVersion not found at: $expectedFileName",
                ClientLogOutput.Level.DEBUG
            )
            null
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
