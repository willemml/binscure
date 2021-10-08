package dev.binclub.binscure.configuration.exclusions

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

class LiteralExclusion(val inPackage: String): ExclusionConfiguration() {
	private fun isExcluded(name: String) = name == inPackage
	private fun cnStr(classNode: ClassNode) = classNode.originalName ?: classNode.name
	
	override fun isExcluded(cn: ClassNode)
		= isExcluded(cnStr(cn))
	override fun isExcluded(cn: ClassNode, mn: MethodNode)
		= isExcluded("${cnStr(cn)}.${mn.name}${mn.desc}")
	override fun isExcluded(cn: ClassNode, fn: FieldNode)
		= isExcluded("${cnStr(cn)}.${fn.name}${fn.desc}")
}
