package dev.binclub.binscure.utils

import dev.binclub.binscure.CObfuscator
import dev.binclub.binscure.configuration.exclusions.ExclusionConfiguration
import dev.binclub.binscure.processors.runtime.OpaqueRuntimeManager
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

/**
 * @author cookiedragon234 30/Jun/2020
 */
fun Collection<ExclusionConfiguration>.isExcluded(classNode: ClassNode) = this.any { it.isExcluded(classNode) }
fun Collection<ExclusionConfiguration>.isExcluded(parentClass: ClassNode, methodNode: MethodNode) = this.any { it.isExcluded(parentClass, methodNode) }
fun Collection<ExclusionConfiguration>.isExcluded(parentClass: ClassNode, fieldNode: FieldNode) = this.any { it.isExcluded(parentClass, fieldNode) }
