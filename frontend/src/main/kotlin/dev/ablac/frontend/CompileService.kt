package dev.ablac.frontend

import dev.ablac.language.IParseService
import dev.ablac.language.nodes.File
import dev.ablac.utils.ILockService
import dev.ablac.utils.IMeasurementService
import dev.ablac.utils.MeasurementScope
import dev.ablac.utils.printFlushed
import kotlinx.coroutines.*
import java.io.InputStream
import java.util.*
import kotlin.coroutines.EmptyCoroutineContext

interface ICompileService {
    suspend fun compileFile(fileName: String, parallel: Boolean = true, compilationContext: CompilationContext? = null)
    suspend fun compileSource(source: String, parallel: Boolean = true, compilationContext: CompilationContext? = null)
    suspend fun compileStream(stream: InputStream, parallel: Boolean = true, compilationContext: CompilationContext? = null)
    fun output()
}

class CompileService(
    private val parserService: IParseService,
    private val lockService: ILockService,
    private val measurementService: IMeasurementService
) : ICompileService{
    private val parentJob = Job()
    private val coroutineScope = CoroutineScope(parentJob + Dispatchers.Default)

    private val pendingCompilation = Collections.synchronizedMap(mutableMapOf<String, PendingCompilationUnit>())
    private val compiledUnits = Collections.synchronizedMap(mutableMapOf<String, CompilationUnit>())
    private var compileNumber: Int = 0
        get() = synchronized(this) {
            return field++
        }

    override suspend fun compileFile(fileName: String, parallel: Boolean, compilationContext: CompilationContext?) {
        compile(fileName, true, parallel, compilationContext) { parserService.parseFile(fileName, it) }
    }

    override suspend fun compileSource(source: String, parallel: Boolean, compilationContext: CompilationContext?) {
        val name = "<source#$compileNumber>"
        compile(name, false, parallel, compilationContext) { parserService.parseSource(name, source, it) }
    }

    override suspend fun compileStream(
        stream: InputStream,
        parallel: Boolean,
        compilationContext: CompilationContext?
    ) {
        val name = "<stream#$compileNumber>"
        compile(name, false, parallel, compilationContext) { parserService.parseStream(name, stream, it) }
    }

    override fun output() {
        runBlocking {
            parentJob.complete()
            parentJob.join()
        }

        println("Compiled Units: ${compiledUnits.size}")
    }

    private suspend fun compile(
        fileName: String,
        checkExistent: Boolean,
        parallel: Boolean,
        compilationContext: CompilationContext?,
        block: suspend (MeasurementScope) -> File
    ) {
        lockService.namedLock(fileName) { lock ->
            if (checkExistent) {
                lock.lock()

                val compilationUnit = compiledUnits[fileName]
                if (compilationUnit != null) {
                    compilationContext?.job?.complete()
                    return@namedLock
                }

                val pending = pendingCompilation[fileName]
                if (pending != null) {
                    if (parallel)
                        pending.job.join()
                    compilationContext?.job?.complete()
                    return@namedLock
                }
            }

            val job = coroutineScope.launch(start = CoroutineStart.LAZY) {
                measurementService.measure("compile $fileName") {
                    val pendingCompilationUnit = pendingCompilation[fileName]
                        ?: throw IllegalStateException("Pending compilation unit is gone.")

                    val file = pendingCompilationUnit.parse(it)
                    val compilationUnit = CompilationUnit(fileName, file)
                    compiledUnits[fileName] = compilationUnit
                    pendingCompilation.remove(fileName)

                    it.measure("type gather") {
                        compilationUnit.file.accept(TypeGather())
                    }
                    printFlushed("gather $fileName")

                    it.measure("execution") {
                        compilationUnit.file.accept(ExecutionVisitor(compilationContext, this@CompileService))
                    }
                    printFlushed("executed $fileName")
                }
            }

            pendingCompilation[fileName] = PendingCompilationUnit(fileName, block, job)

            job.start()

            if (checkExistent)
                lock.unlock()

            if (!parallel)
                job.join()
        }
    }

    data class PendingCompilationUnit(val fileName: String, val parse: suspend (MeasurementScope) -> File, var job: Job)
}

data class CompilationContext(val parentJob: Job, val job: CompletableJob)
