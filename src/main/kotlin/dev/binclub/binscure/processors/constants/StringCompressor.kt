package dev.binclub.binscure.processors.constants

import dev.binclub.binscure.CObfuscator
import dev.binclub.binscure.IClassProcessor
import dev.binclub.binscure.api.TransformerConfiguration
import dev.binclub.binscure.classpath.ClassSources
import dev.binclub.binscure.configuration.ConfigurationManager.rootConfig
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodNode

/**
 * @author cookiedragon234 28/Mar/2020
 */
class StringCompressor(source: ClassSources): IClassProcessor(source) {
	override val progressDescription: String
		get() = "Compressing string constants"
	override val config: TransformerConfiguration
		get() = rootConfig // TODO: Add config
	
	override fun process(
		source: ClassSources,
		classes: MutableCollection<ClassNode>,
		passThrough: MutableMap<String, ByteArray>
	) {
		/*val strings: MutableMap<String, MutableCollection<StringInfo>> = hashMapOf()
		
		for (classNode in classes) {
			if (isExcluded(classNode))
				continue
			
			for (method in classNode.methods) {
				if (isExcluded(classNode, method))
					continue
				
				for (insn in method.instructions) {
					if (insn is LdcInsnNode && insn.cst is String) {
						val str = insn.cst as String
						
						strings.getOrPut(str, { hashSetOf() }).add(StringInfo(insn, method))
					}
				}
			}
		}
		
		if (strings.isNotEmpty()) {
			var classNode: ClassNode? = null
			var size = 0
			
			for ((string, instances) in strings) {
				if (classNode == null || size > (65535/2)) {
					classNode = ClassNode().apply {
						access = ACC_PUBLIC
						name = classRenamer.namer.uniqueUntakenClass(source)
						superName = "java/lang/Object"
					}
				}
				
				for ((insn, method) in instances) {
				
				}
			}
		}*/
	}
	
	private data class StringInfo(
		val insn: LdcInsnNode,
		val method: MethodNode
	)
	
	val biggestChar = 0x10FFFF
}
