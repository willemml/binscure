package dev.binclub.binscure.processors.constants

import dev.binclub.binscure.IClassProcessor
import dev.binclub.binscure.api.TransformerConfiguration
import dev.binclub.binscure.classpath.ClassSources
import dev.binclub.binscure.configuration.ConfigurationManager.rootConfig
import dev.binclub.binscure.utils.*
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.*

/**
 * This transformer removes the default values from fields and instead assigns their value inside the static initializer
 *
 * The following code:
 * ```Java
 * int i = 0;
 * ```
 *
 * becomes:
 * ```Java
 * int i;
 *
 * static {
 *  i = 0;
 * }
 * ```
 *
 * @author cookiedragon234 07/Mar/2020
 */
class FieldInitializer(source: ClassSources): IClassProcessor(source) {
	override val progressDescription: String
		get() = "Moving field constants to the static initializer"
	override val config: TransformerConfiguration
		get() = rootConfig
	
	override fun process(
		source: ClassSources,
		classes: MutableCollection<ClassNode>,
		passThrough: MutableMap<String, ByteArray>
	) {
		for (classNode in classes) {
			if (isExcluded(classNode))
				continue
			
			val staticFields = arrayListOf<FieldNode>()
			val instanceFields = arrayListOf<FieldNode>()
			for (field in classNode.fields) {
				if (isExcluded(classNode, field) || field.value == null)
					continue
				
				if (field.access.hasAccess(ACC_STATIC)) {
					staticFields.add(field)
				} else {
					instanceFields.add(field)
				}
			}
			
			if (staticFields.isNotEmpty()) {
				val clinit = getClinit(classNode)
				clinit.instructions.apply {
					for (field in staticFields) {
						insert(FieldInsnNode(PUTSTATIC, classNode.name, field.name, field.desc))
						insert(when (field.value) {
							is Int -> ldcInt(field.value as Int)
							is Double -> ldcDouble(field.value as Double)
							is Long -> ldcLong(field.value as Long)
							else -> LdcInsnNode(field.value)
						})
						
						field.value = null
					}
				}
			}
			
			if (instanceFields.isNotEmpty()) {
				val builder = InsnBuilder()
				for (field in instanceFields) {
					builder.aload(0)
					builder.ldc_unsafe(field.value)
					builder.putfield(classNode.name, field.name, field.desc)
				}
				val list = builder.list
				
				var inserted = false
				methodLoop@for (method in classNode.methods) {
					if (method.name == "<init>" && method.instructions != null) {
						for (insn in method.instructions) {
							if (insn.opcode == INVOKESPECIAL) {
								val insn = insn as MethodInsnNode
								if (insn.owner == classNode.superName && insn.name == "<init>") {
									// insert after the super call
									method.instructions.insert(insn, list)
									inserted = true
									continue@methodLoop
								}
							}
						}
					}
				}
				
				// If we managed to insert it
				if (inserted) {
					for (field in instanceFields) {
						field.value = null
					}
				} else {
					println("\rCouldn't insert field initializer for ${classNode.niceName}")
				}
			}
		}
	}
}
