/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.sourceSections

import com.intellij.openapi.vfs.StandardFileSystems
import junit.framework.TestCase
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.*
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoot
import org.jetbrains.kotlin.codegen.forTestCompile.ForTestCompileRuntime
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.config.addKotlinSourceRoots
import org.jetbrains.kotlin.daemon.client.DaemonReportingTargets
import org.jetbrains.kotlin.daemon.client.KotlinCompilerClient
import org.jetbrains.kotlin.daemon.common.*
import org.jetbrains.kotlin.integration.KotlinIntegrationTestBase.getCompilerLib
import org.jetbrains.kotlin.script.StandardScriptDefinition
import org.jetbrains.kotlin.test.ConfigurationKind
import org.jetbrains.kotlin.test.KotlinTestUtils
import org.jetbrains.kotlin.test.TestCaseWithTmpdir
import org.jetbrains.kotlin.test.TestJdkKind
import org.jetbrains.kotlin.utils.tryConstructClassFromStringArgs
import java.io.*
import java.lang.management.ManagementFactory
import java.net.URLClassLoader
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

class SourceSectionsTest : TestCaseWithTmpdir() {

    companion object {
        val TEST_ALLOWED_SECTIONS = listOf("let", "apply") // using standard function names that can be used as sections in the script context without crafting special ones
        val TEST_DATA_DIR = File(KotlinTestUtils.getHomeDirectory(), "plugins/source-sections/source-sections-compiler/testData")
    }

    private val compilerLibDir = getCompilerLib()

    val compilerClassPath = listOf(
            File(compilerLibDir, "kotlin-compiler.jar"))
    val scriptRuntimeClassPath = listOf(
            File(compilerLibDir, "kotlin-runtime.jar"),
            File(compilerLibDir, "kotlin-script-runtime.jar"))
    val sourceSectionsPluginJar =
            File(compilerLibDir, "source-sections-compiler-plugin.jar")
    val compilerId by lazy(LazyThreadSafetyMode.NONE) { CompilerId.makeCompilerId(compilerClassPath) }

    private fun createEnvironment(vararg sources: String, withSourceSectionsPlugin: Boolean = true): KotlinCoreEnvironment {
        val configuration = KotlinTestUtils.newConfiguration(ConfigurationKind.JDK_NO_RUNTIME, TestJdkKind.FULL_JDK,
                                                             ForTestCompileRuntime.scriptRuntimeJarForTests(),
                                                             ForTestCompileRuntime.runtimeJarForTests())

        configuration.addJvmClasspathRoot(ForTestCompileRuntime.kotlinTestJarForTests())

        configuration.addKotlinSourceRoots(sources.asList())
        configuration.put<MessageCollector>(
                CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY,
                PrintingMessageCollector(System.err, MessageRenderer.PLAIN_FULL_PATHS, false)
        )
        configuration.add(JVMConfigurationKeys.SCRIPT_DEFINITIONS, StandardScriptDefinition)
        if (withSourceSectionsPlugin) {
            configuration.addAll(SourceSectionsConfigurationKeys.SECTIONS_OPTION, TEST_ALLOWED_SECTIONS)
            configuration.add(ComponentRegistrar.PLUGIN_COMPONENT_REGISTRARS, SourceSectionsComponentRegistrar())
        }

        val environment = KotlinCoreEnvironment.createForTests(testRootDisposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES)
        return environment
    }

    private data class SourceToExpectedResults(val source: File, val expectedResults: File)

    private fun getTestFiles(expectedExt: String): List<SourceToExpectedResults> {
        val testDataFiles = TEST_DATA_DIR.listFiles()
        val sourceToExpected = testDataFiles.filter { it.isFile && it.extension == "kts" }
                .mapNotNull { testFile ->
                    testDataFiles.find { it.isFile && it.name == testFile.name + expectedExt }?.let { SourceToExpectedResults(testFile, it) }
                }
        TestCase.assertTrue("No test files found", sourceToExpected.isNotEmpty())
        return sourceToExpected
    }

    private fun useLinesFromStream(actualStream: InputStream, charset: Charset): ArrayList<String> {
        val actual = arrayListOf<String>()
        actualStream.use { actualStream ->
            val reader = BufferedReader(actualStream.reader(charset))
            while (true) {
                val line = reader.readLine() ?: break
                actual.add(line.trimEnd())
            }
        }
        return actual
    }

    fun testSourceSectionsFilter() {
        val sourceToFiltered = getTestFiles(".filtered")

        createEnvironment() // creates VirtualFileManager
        val fileCreator = FilteredSectionsVirtualFileExtension(TEST_ALLOWED_SECTIONS.toSet())

        sourceToFiltered.forEach { (source, expectedResult) ->
            val filteredVF = fileCreator.createPreprocessedFile(StandardFileSystems.local().findFileByPath(source.canonicalPath))
            TestCase.assertNotNull("Cannot generate preprocessed file", filteredVF)
            val expected = useLinesFromStream(expectedResult.inputStream(), Charset.defaultCharset())
            val bytes = filteredVF!!.contentsToByteArray()
            val actual = useLinesFromStream(ByteArrayInputStream(bytes), filteredVF.charset)
            TestCase.assertEquals("Unexpected result on preprocessing file '${source.name}'", expected, actual)
        }
    }

    fun testSourceSectionsRun() {
        val sourceToOutput = getTestFiles(".out")

        sourceToOutput.forEach { (source, expectedOutput) ->
            val environment = createEnvironment(source.canonicalPath)
            val scriptClass = KotlinToJVMBytecodeCompiler.compileScript(environment, Thread.currentThread().contextClassLoader)
            TestCase.assertNotNull("Compilation errors", scriptClass)
            verifyScriptOutput(scriptClass, expectedOutput)
        }
    }

    fun testSourceSectionsRunBench() {
        val mxBeans = ManagementFactory.getThreadMXBean()
        val (source, _) = getTestFiles(".out").first()

        // warming up application environment
        KotlinToJVMBytecodeCompiler.compileScript(createEnvironment(source.canonicalPath, withSourceSectionsPlugin = false), Thread.currentThread().contextClassLoader)

        val times = generateSequence {
            val t0 = mxBeans.threadCpuTime()
            KotlinToJVMBytecodeCompiler.compileScript(createEnvironment(source.canonicalPath, withSourceSectionsPlugin = false), Thread.currentThread().contextClassLoader)
            val t1 = mxBeans.threadCpuTime()
            KotlinToJVMBytecodeCompiler.compileScript(createEnvironment(source.canonicalPath, withSourceSectionsPlugin = true), Thread.currentThread().contextClassLoader)
            val t2 = mxBeans.threadCpuTime()
            Triple(t1 - t0, t2 - t1, t2 - t1)
        }.take(10).toList()

        val adjustedMaxDiff = times.sortedByDescending { (_, _, diff) -> diff }.drop(2).first()

        fun Long.ms() = TimeUnit.NANOSECONDS.toMillis(this)
        TestCase.assertTrue("sourceSections plugin brings too much overheads: ${times.joinToString { "(${it.first.ms()}, ${it.second.ms()})" }} (expecting it to be faster than regular compilation due to less lines compiled)",
                            adjustedMaxDiff.third < 20 /* assuming it measurement error */ || adjustedMaxDiff.first >= adjustedMaxDiff.second )
    }

    fun testSourceSectionCompileLocal() {
        val sourceToOutput = getTestFiles(".out")

        val messageCollector = TestMessageCollector()
        sourceToOutput.forEach { (source, expectedOutput) ->

            val args = arrayOf(source.canonicalPath, "-d", tmpdir.canonicalPath,
                               "-Xplugin", sourceSectionsPluginJar.canonicalPath,
                               "-P", TEST_ALLOWED_SECTIONS.joinToString(",") { "plugin:${SourceSectionsCommandLineProcessor.PLUGIN_ID}:${SourceSectionsCommandLineProcessor.SECTIONS_OPTION.name}=$it" })
            messageCollector.clear()
            val code = K2JVMCompiler().exec(messageCollector,
                                            Services.EMPTY,
                                            K2JVMCompilerArguments().apply { K2JVMCompiler().parseArguments(args, this) }).code
            val outputs = messageCollector.messages.filter { it.severity == CompilerMessageSeverity.OUTPUT }.mapNotNull {
                OutputMessageUtil.parseOutputMessage(it.message)?.let { outs ->
                    outs.outputFile?.let { OutputMessageUtil.Output(outs.sourceFiles, it) }
                }
            }

            val scriptClass = verifyAndLoadClass(code, messageCollector, outputs, source)

            verifyScriptOutput(scriptClass, expectedOutput)
        }
    }

    fun testSourceSectionCompileOnDaemon() {
        val sourceToOutput = getTestFiles(".out")

        withFlagFile("sourceSections", ".alive") { aliveFile ->

            val daemonOptions = DaemonOptions(runFilesPath = File(tmpdir, getTestName(true)).absolutePath, verbose = true, reportPerf = true)
            val daemonJVMOptions = configureDaemonJVMOptions(inheritMemoryLimits = false, inheritAdditionalProperties = false)
            val messageCollector = TestMessageCollector()

            val daemonWithSession = KotlinCompilerClient.connectAndLease(compilerId, aliveFile, daemonJVMOptions, daemonOptions,
                                                                         DaemonReportingTargets(messageCollector = messageCollector), autostart = true, leaseSession = true)
            assertNotNull("failed to connect daemon", daemonWithSession)

            try {

                sourceToOutput.forEach { (source, expectedOutput) ->

                    val args = arrayOf(source.canonicalPath, "-d", tmpdir.canonicalPath,
                                       "-Xplugin", sourceSectionsPluginJar.canonicalPath,
                                       "-P", TEST_ALLOWED_SECTIONS.joinToString(",") { "plugin:${SourceSectionsCommandLineProcessor.PLUGIN_ID}:${SourceSectionsCommandLineProcessor.SECTIONS_OPTION.name}=$it" })

                    messageCollector.clear()
                    val outputs = arrayListOf<OutputMessageUtil.Output>()

                    val code = KotlinCompilerClient.compile(daemonWithSession!!.service, daemonWithSession.sessionId, CompileService.TargetPlatform.JVM,
                                                            args, messageCollector,
                                                            { outFile, srcFiles -> outputs.add(OutputMessageUtil.Output(srcFiles, outFile)) },
                                                            reportSeverity = ReportSeverity.DEBUG)

                    val scriptClass = verifyAndLoadClass(code, messageCollector, outputs, source)

                    verifyScriptOutput(scriptClass, expectedOutput)
                }
            }
            finally {
                daemonWithSession!!.service.shutdown()
            }
        }
    }

    private fun verifyAndLoadClass(code: Int, messageCollector: TestMessageCollector, outputs: List<OutputMessageUtil.Output>, source: File): Class<*>? {
        TestCase.assertEquals("Compilation failed:\n${messageCollector.messages.joinToString("\n")}", 0, code)
        TestCase.assertFalse("Compilation failed:\n${messageCollector.messages.joinToString("\n")}", messageCollector.hasErrors())
        val scriptClassFile = outputs.first().outputFile
        TestCase.assertEquals("unexpected class file generated", source.nameWithoutExtension.capitalize(), scriptClassFile?.nameWithoutExtension)

        val cl = URLClassLoader((scriptRuntimeClassPath + tmpdir).map { it.toURI().toURL() }.toTypedArray())
        val scriptClass = cl.loadClass(scriptClassFile!!.nameWithoutExtension)

        TestCase.assertNotNull("Unable to load class $scriptClassFile", scriptClass)
        return scriptClass
    }

    private fun verifyScriptOutput(scriptClass: Class<*>?, expectedOutput: File) {
        val scriptOut = captureOut {
            tryConstructClassFromStringArgs(scriptClass!!, emptyList())
        }
        val expected = expectedOutput.readText()

        TestCase.assertEquals(expected, scriptOut)
    }
}

internal inline fun withFlagFile(prefix: String, suffix: String? = null, body: (File) -> Unit) {
    val file = createTempFile(prefix, suffix)
    try {
        body(file)
    }
    finally {
        file.delete()
    }
}

internal fun captureOut(body: () -> Unit): String {
    val outStream = ByteArrayOutputStream()
    val prevOut = System.out
    System.setOut(PrintStream(outStream))
    try {
        body()
    }
    finally {
        System.out.flush()
        System.setOut(prevOut)
    }
    return outStream.toString()
}

class TestMessageCollector : MessageCollector {

    data class Message(val severity: CompilerMessageSeverity, val message: String, val location: CompilerMessageLocation)

    val messages = arrayListOf<Message>()

    override fun clear() {
        messages.clear()
    }

    override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageLocation) {
        messages.add(Message(severity, message, location))
    }

    override fun hasErrors(): Boolean = messages.any { it.severity == CompilerMessageSeverity.EXCEPTION || it.severity == CompilerMessageSeverity.ERROR }
}
