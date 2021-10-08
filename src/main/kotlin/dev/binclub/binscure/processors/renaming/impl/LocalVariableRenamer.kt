package dev.binclub.binscure.processors.renaming.impl

import dev.binclub.binscure.IClassProcessor
import dev.binclub.binscure.classpath.ClassSources
import dev.binclub.binscure.configuration.ConfigurationManager.rootConfig
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode

/**
 * @author cookiedragon234 22/Jan/2020
 */
class LocalVariableRenamer(source: ClassSources): IClassProcessor(source) {
	override val progressDescription: String
		get() = "Renaming local variables"
	override val config = rootConfig.remap
	
	override fun process(
		source: ClassSources,
		classes: MutableCollection<ClassNode>,
		passThrough: MutableMap<String, ByteArray>
	) {
		if (!config.areLocalsEnabled())
			return
		
		val name = config.localVariableName
		for (classNode in classes) {
			if (isExcluded(classNode))
				continue
			
			for (method in classNode.methods) {
				if (isExcluded(classNode, method))
					continue
				
				val nameMap = mutableMapOf<String, String>()
				
				if (name.isEmpty()) {
					method.localVariables = null
				} else {
					for (localVariable in method.localVariables ?: continue) {
						nameMap[localVariable.name] = "$name:${localVariable.desc}"
						localVariable.name = name
					}
				}
				
				if (method.parameters != null) {
					for (parameter in method.parameters) {
						parameter.name = name
					}
				}
				
				for (insn in method.instructions) {
					if (insn is LdcInsnNode && insn.cst is String) {
						val nextInsn = insn.next
						if (
							nextInsn != null
							&&
							nextInsn is MethodInsnNode
							&&
							nextInsn.owner == "kotlin/jvm/internal/Intrinsics"
							&&
							nextInsn.name == "checkParameterIsNotNull"
						) {
							val cst = insn.cst as String
							insn.cst = nameMap.getOrDefault(cst, "[Removed By CObf]")
						}
					}
				}
			}
		}
	}
}
