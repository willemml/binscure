package dev.binclub.binscure

import dev.binclub.binscure.api.RootConfiguration
import dev.binclub.binscure.classpath.ClassSources
import dev.binclub.binscure.classpath.ClassPathIO
import dev.binclub.binscure.configuration.ConfigurationManager.rootConfig
import dev.binclub.binscure.processors.arithmetic.ArithmeticSubstitutionTransformer
import dev.binclub.binscure.processors.arithmetic.MbaTransformer
import dev.binclub.binscure.processors.classmerge.StaticMethodMerger
import dev.binclub.binscure.processors.constants.FieldInitializer
import dev.binclub.binscure.processors.constants.NumberObfuscation
import dev.binclub.binscure.processors.constants.string.StringObfuscator
import dev.binclub.binscure.processors.debug.AccessStripper
import dev.binclub.binscure.processors.debug.KotlinMetadataStripper
import dev.binclub.binscure.processors.debug.SourceStripper
import dev.binclub.binscure.processors.exploit.BadAttributeExploit
import dev.binclub.binscure.processors.exploit.BadIndyConstant
import dev.binclub.binscure.processors.flow.CfgFucker
import dev.binclub.binscure.processors.flow.MethodParameterObfuscator
import dev.binclub.binscure.processors.flow.classinit.ClassInitMonitor
import dev.binclub.binscure.utils.whenNotNull
import dev.binclub.binscure.processors.flow.trycatch.FakeTryCatch
import dev.binclub.binscure.processors.flow.trycatch.TryCatchDuplication
import dev.binclub.binscure.processors.flow.trycatch.UselessTryCatch
import dev.binclub.binscure.processors.indirection.DynamicCallObfuscation
import dev.binclub.binscure.processors.indirection.DynamicFieldObfuscation
import dev.binclub.binscure.processors.indirection.DynamicVariableObfuscation
import dev.binclub.binscure.processors.optimisers.EnumValuesOptimiser
import dev.binclub.binscure.processors.renaming.generation.NameGenerator
import dev.binclub.binscure.processors.renaming.impl.LocalVariableRenamer
import dev.binclub.binscure.processors.resources.ManifestResourceProcessor
import dev.binclub.binscure.processors.runtime.*
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.PrintWriter
import java.lang.reflect.Modifier
import java.security.SecureRandom

/**
 * @author cookiedragon234 20/Jan/2020
 */
@Suppress("NOTHING_TO_INLINE")
object CObfuscator {
	val DEBUG = (System.getProperty("debug")?.isEmpty() == false)
	
	val random = SecureRandom()
	val mappings = mutableMapOf<String, String>()
	lateinit var hashParts: IntArray
	
	fun obfuscate(config: RootConfiguration) {
		rootConfig = config
		
		val license = Licenser.license
		if (license == null) {
			println("Invalid License File. Please email x4e_x4e@protonmail.com for assistance.")
			return
		} else {
			println("Found valid username and password at ${Licenser.licenseFile}")
		}
		hashParts = license.hashParts
		
		val classPath = HashMap<String, ClassNode>()
		for (i in 0..(hashParts[8] xor 596941)) { // for i in 0..0
			ClassPathIO.loadClassPath(classPath, rootConfig.libraries)
		}
		
		rootConfig.sources.forEach { (input, output) ->
			obfuscate(classPath, input, output)
		}
		
		rootConfig.mappingFile.whenNotNull { file ->
			try {
				PrintWriter(FileOutputStream(file)).use {
					for ((key, value) in mappings) {
						it.println(key.replace(",", "\\,") + "," + value.replace(",", "\\,"))
					}
				}
			} catch (t: Throwable) {
				Exception("Error writing mapping file", t).printStackTrace()
			}
		}
	}
	
	lateinit var classNamer: NameGenerator
	lateinit var opaqueRuntimeManager: OpaqueRuntimeManager
	lateinit var processorMap: Map<Class<*>, IClassProcessor>
	inline fun <reified T: IClassProcessor> processor(): T {
		return processorMap[T::class.java] as T
	}
	
	fun obfuscate(classPath: Map<String, ClassNode>, input: File, output: File) {
		if (!input.exists())
			throw FileNotFoundException("Input file $input does not exist")
		println("\rObfuscating $input...")
		if (output.exists()) {
			if (!output.renameTo(output))
				System.err.println("Warning: Output file $output is currently in use by another process")
			else
				System.err.println("Warning: Output file $output already exists, will be overwritten")
		}
		
		val classSources = ClassSources(classPath)
		opaqueRuntimeManager = OpaqueRuntimeManager(classSources)
		ClassPathIO.loadInputJar(classSources, input)
		classSources.reconstructHierarchy()
		classNamer = NameGenerator(rootConfig.remap.classPrefix)
		
		val processors = arrayOf(
			DynamicVariableObfuscation(classSources),
			
			FieldInitializer(classSources),
			AccessStripper(classSources),
			EnumValuesOptimiser(classSources),
			
			SourceStripper(classSources),
			KotlinMetadataStripper(classSources),
			
			MethodParameterObfuscator(classSources),
			
			LocalVariableRenamer(classSources),
			
			StringObfuscator(classSources),
			DynamicCallObfuscation(classSources),
			DynamicFieldObfuscation(classSources),
			
			ArithmeticSubstitutionTransformer(classSources),
			CfgFucker(classSources),
			ClassInitMonitor(classSources),
			FakeTryCatch(classSources),
			UselessTryCatch(classSources),
			TryCatchDuplication(classSources),
			
			StaticMethodMerger(classSources),
			NumberObfuscation(classSources),
			
			BadAttributeExploit(classSources),
			BadIndyConstant(classSources),
			MbaTransformer(classSources),
			
			//LoopUnroller,
			//AbstractMethodImplementor, this is dumb
			
			ManifestResourceProcessor(classSources)
		)
		processorMap = HashMap<Class<*>, IClassProcessor>(processors.size).also {
			for (p in processors) {
				it[p.javaClass] = p
			}
		}
		
		ArrayList<ClassNode>(classSources.classes.values).let { classes ->
			if (classes.isNotEmpty()) {
				var progress = 0f
				for (processor in processors) {
					try {
						debug(processor::class.java.simpleName)
						if (rootConfig.printProgress && processor.config.enabled) {
							print(rootConfig.getLineChar())
							val percentStr = ((progress / processors.size) * 100).toInt().toString().padStart(3, ' ')
							print("$percentStr% - ${processor.progressDescription}".padEnd(100, ' '))
						}
						for (i in 0 until (hashParts[0] - 0x9121)) { // for i in 0 until 1
							processor.process(classSources, classes, classSources.passThrough)
						}
					} catch (t: Throwable) {
						println("\rException while processing [${processor.progressDescription}]:")
						t.printStackTrace()
					}
					progress += 1
				}
				if (rootConfig.printProgress) {
					print(rootConfig.getLineChar())
				}
			}
		}
		opaqueRuntimeManager.getClassNodeSafe()?.let { opaque ->
			classSources.classes[opaque.name] = opaque
		}
		
		rootConfig.postProcessor(classSources.classes, classSources.passThrough)
		
		ClassPathIO.writeOutput(classSources, output)
		println("\rWrote obfuscated output to $output")
	}
	
	inline fun noMethodInsns(methodNode: MethodNode) =
		Modifier.isAbstract(methodNode.access) || Modifier.isNative(methodNode.access) || methodNode.instructions == null
	
	inline fun randomWeight(weight: Int): Boolean {
		return random.nextInt(weight) == 0
	}

	@Suppress("ConstantConditionIf")
	inline fun debug(message: Any) {
		if (false) {
			println(message)
		}
	}
}
