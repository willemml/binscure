package dev.binclub.binscure.configuration.exclusions

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.AnnotationNode

class AnnotationExclusion(val annot: String): ExclusionConfiguration() {
	val descAnnot = "L$annot;"
	
	private fun isExcluded(annos: List<AnnotationNode>?): Boolean
		= annos != null && annos.any { descAnnot == it.desc }
	
	override fun isExcluded(cn: ClassNode)
		= isExcluded(cn.invisibleAnnotations) || isExcluded(cn.visibleAnnotations)
	override fun isExcluded(cn: ClassNode, mn: MethodNode)
		= isExcluded(mn.invisibleAnnotations) || isExcluded(mn.visibleAnnotations)
	override fun isExcluded(cn: ClassNode, fn: FieldNode)
		= isExcluded(fn.invisibleAnnotations) || isExcluded(fn.visibleAnnotations)
}
