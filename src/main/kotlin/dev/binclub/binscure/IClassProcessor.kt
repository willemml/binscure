package dev.binclub.binscure

import dev.binclub.binscure.api.TransformerConfiguration
import dev.binclub.binscure.classpath.ClassSources
import dev.binclub.binscure.configuration.ConfigurationManager.rootConfig
import dev.binclub.binscure.utils.isExcluded
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import java.security.SecureRandom

/**
 * @author cookiedragon234 20/Jan/2020
 */
abstract class IClassProcessor(val source: ClassSources) {
	val random: SecureRandom
		get() = CObfuscator.random
	
	abstract val progressDescription: String
	abstract val config: TransformerConfiguration
	abstract fun process(source: ClassSources, classes: MutableCollection<ClassNode>, passThrough: MutableMap<String, ByteArray>)
	
	fun isExcluded(classNode: ClassNode): Boolean {
		return if (config != rootConfig) {
			rootConfig.tExclusions.isExcluded(classNode) || config.tExclusions.isExcluded(classNode)
		} else {
			config.tExclusions.isExcluded(classNode)
		}
	}
	fun isExcluded(classNode: ClassNode, methodNode: MethodNode): Boolean {
		return if (config != rootConfig) {
			rootConfig.tExclusions.isExcluded(classNode, methodNode) || config.tExclusions.isExcluded(classNode, methodNode)
		} else {
			config.tExclusions.isExcluded(classNode, methodNode)
		}
	}
	fun isExcluded(classNode: ClassNode, fieldNode: FieldNode): Boolean {
		return if (config != rootConfig) {
			rootConfig.tExclusions.isExcluded(classNode, fieldNode) || config.tExclusions.isExcluded(classNode, fieldNode)
		} else {
			config.tExclusions.isExcluded(classNode, fieldNode)
		}
	}
}

inline fun IClassProcessor.forClass(classes: Collection<ClassNode>, op: (ClassNode) -> Unit) {
	classes.forEach { cn ->
		if (!isExcluded(cn)) {
			op(cn)
		}
	}
}

inline fun IClassProcessor.forMethod(cn: ClassNode, op: (MethodNode) -> Unit) {
	cn.methods.forEach { mn ->
		if (!isExcluded(cn, mn)) {
			op(mn)
		}
	}
}

inline fun IClassProcessor.forField(cn: ClassNode, op: (FieldNode) -> Unit) {
	cn.fields.forEach { mn ->
		if (!isExcluded(cn, mn)) {
			op(mn)
		}
	}
}
