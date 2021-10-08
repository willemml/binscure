package dev.binclub.binscure.configuration.exclusions

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

class StartExclusion(val inPackage: String, val notExact: Boolean): ExclusionConfiguration() {
	private fun isExcluded(name: String): Boolean {
		if (notExact && name.length == inPackage.length) {
			return false
		}
		return name.startsWith(inPackage)
	}
	private fun cnStr(classNode: ClassNode) = classNode.originalName ?: classNode.name
	
	override fun isExcluded(cn: ClassNode)
		= isExcluded(cnStr(cn))
	override fun isExcluded(cn: ClassNode, mn: MethodNode)
		= isExcluded("${cnStr(cn)}.${mn.name}${mn.desc}")
	override fun isExcluded(cn: ClassNode, fn: FieldNode)
		= isExcluded("${cnStr(cn)}.${fn.name}${fn.desc}")
}
