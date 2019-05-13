package se.eelde.ago

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.TaskAction
import se.eelde.ago.evaluators.DaemonExecutionEvaluator
import se.eelde.ago.evaluators.MemoryEvaluator
import se.eelde.ago.evaluators.StartParameterEvaluator

open class AgoTask : DefaultTask() {

    internal lateinit var agoOutputter: AgoOutputter
    internal lateinit var agoPluginExtension: AgoPluginExtension

    @TaskAction
    fun moduleTask() {

        agoOutputter.greatInfo()

        var optimizationsMissing = false

        val daemonCheck = Check.Daemon()
        val daemonExecutionEvaluator = DaemonExecutionEvaluator(project as DefaultProject)
        if (daemonExecutionEvaluator.isSingleUse) {
            agoOutputter.printCheck(daemonCheck)

            if (daemonCheck.aDefault == CheckSeverity.ENABLED_ENFORCED) {
                optimizationsMissing = true
            }
        } else {
            agoOutputter.printPraise(daemonCheck)
        }

        val startParameterEvaluator = StartParameterEvaluator(project as DefaultProject)

        val parallelCheck = Check.Parallel()
        if (!startParameterEvaluator.isParalellExecutionEnabled) {
            agoOutputter.printCheck(parallelCheck)
            if (parallelCheck.aDefault == CheckSeverity.ENABLED_ENFORCED) {
                optimizationsMissing = true
            }
        } else {
            agoOutputter.printPraise(parallelCheck)
        }

        val cacheCheck = Check.Cache()
        if (!startParameterEvaluator.isCachingEnabled) {
            agoOutputter.printCheck(cacheCheck)
            if (cacheCheck.aDefault == CheckSeverity.ENABLED_ENFORCED) {
                optimizationsMissing = true
            }
        } else {
            agoOutputter.printPraise(cacheCheck)
        }

        val configureOnDemandCheck = Check.ConfigureOnDemand()
        if (!startParameterEvaluator.isConfigureOnDemand) {
            agoOutputter.printCheck(configureOnDemandCheck)
            if (configureOnDemandCheck.aDefault == CheckSeverity.ENABLED_ENFORCED) {
                optimizationsMissing = true
            }
        } else {
            agoOutputter.printPraise(configureOnDemandCheck)
        }

        val memoryEvaluator = MemoryEvaluator(project as DefaultProject)

        val jvmMemory = memoryEvaluator.getMaxMemory / 1000000

        if (project.extensions.extraProperties.has("org.gradle.jvmargs")) {
            val jvmArgs = project.extensions.extraProperties["org.gradle.jvmargs"]?.toString() ?: ""
            val jvmArgsParser = JvmArgsParser()
            val parseMaxJmvMem = jvmArgsParser.parseMaxJmvMem(jvmArgs = jvmArgs)
            val parseFileEncoding = jvmArgsParser.parseFileEncoding(jvmArgs = jvmArgs)

            agoOutputter.logger.log(LogLevel.DEBUG, "Defined Jvm Memory according to memoryEvaluator: $jvmMemory")
            agoOutputter.logger.log(LogLevel.DEBUG, "jvmArgsParser mem: $parseMaxJmvMem")
            agoOutputter.logger.log(LogLevel.DEBUG, "jvmArgsParser enc: $parseFileEncoding")

            val expectedJvmMemory = Check.Memory(Memory.Gigabyte(2))
            if (parseMaxJmvMem.asBytes() < expectedJvmMemory.size.asBytes()) {
                agoOutputter.printCheck(expectedJvmMemory)
                if (expectedJvmMemory.aDefault == CheckSeverity.ENABLED_ENFORCED) {
                    optimizationsMissing = true
                }
            } else {
                agoOutputter.printPraise(expectedJvmMemory)
            }

            val utF8FileEncodingCheck = Check.UTF8FileEncoding(Charsets.UTF_8)
            if (parseFileEncoding != utF8FileEncodingCheck.charset) {
                agoOutputter.printCheck(utF8FileEncodingCheck)
                if (utF8FileEncodingCheck.aDefault == CheckSeverity.ENABLED_ENFORCED) {
                    optimizationsMissing = true
                }
            } else {
                agoOutputter.printPraise(utF8FileEncodingCheck)
            }
        }

        val versionsUpdatePluginCheck = Check.VersionsPlugin()
        @Suppress("SENSELESS_COMPARISON")
        if (!project.plugins.hasPlugin("com.github.ben-manes.versions")) {
            agoOutputter.printCheck(versionsUpdatePluginCheck)
        } else {
            agoOutputter.printPraise(versionsUpdatePluginCheck)
        }

        val noCrash = agoPluginExtension.shouldSkipOptimizations(System.getenv())

        if (optimizationsMissing) {
            if (noCrash) {
                agoOutputter.logger.log(LogLevel.LIFECYCLE, "Task not crashing because ${agoPluginExtension.skipOptimizationsEnvVar} is set to 'true'")
            } else {
                throw GradleException("Missing required optimizations - check logs")
            }
        }
    }
}
