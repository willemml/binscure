package dev.binclub.binscure.processors.classmerge

import dev.binclub.binscure.CObfuscator
import dev.binclub.binscure.IClassProcessor
import dev.binclub.binscure.api.transformers.FlowObfuscationConfiguration
import dev.binclub.binscure.api.transformers.MergeMethods.NONE
import dev.binclub.binscure.classpath.ClassSources
import dev.binclub.binscure.classpath.ClassTree
import dev.binclub.binscure.configuration.ConfigurationManager.rootConfig
import dev.binclub.binscure.processors.constants.string.StringObfuscator
import dev.binclub.binscure.processors.renaming.generation.NameGenerator
import dev.binclub.binscure.utils.*
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.Type.getArgumentTypes
import org.objectweb.asm.Type.getReturnType
import org.objectweb.asm.tree.*

/**
 * @author cookiedragon234 21/Feb/2020
 */
class StaticMethodMerger(source: ClassSources): IClassProcessor(source) {
	private fun containsSpecial(source: ClassSources, classNode: ClassNode, insnList: InsnList): Boolean {
		val hierarchy = source.getHierarchy(classNode.name)?.allParents
		for (insn in insnList) {
			if (insn.opcode == INVOKESPECIAL || insn.opcode == INVOKEDYNAMIC) return true
			
			if (insn is FieldInsnNode) {
				if (hierarchy?.contains(insn.owner) == true) {
					return true
				}
			}
			if (insn is MethodInsnNode) {
				if (hierarchy?.contains(insn.owner) == true) {
					return true
				}
			}
		}
		
		return false
	}
	
	override val progressDescription: String
		get() = "Merging methods"
	override val config: FlowObfuscationConfiguration = rootConfig.flowObfuscation
	
	val OBJECT_TYPE = Type.getType("Ljava/lang/Object;")
	
	override fun process(
		source: ClassSources,
		classes: MutableCollection<ClassNode>,
		passThrough: MutableMap<String, ByteArray>
	) {
		if (!config.enabled || config.mergeMethods == NONE) {
			return
		}
		
		val stringObfuscator = CObfuscator.processor<StringObfuscator>()
		val staticMethods: MutableMap<String, MutableSet<Pair<ClassNode, MethodNode>>> = hashMapOf()
		
		for (classNode in classes) {
			if (isExcluded(classNode))
				continue
			
			for (method in classNode.methods) {
				if (isExcluded(classNode, method))
					continue
				
				if (
					!method.name.startsWith('<')
					&&
					!method.access.hasAccess(ACC_ABSTRACT)
					&&
					!method.access.hasAccess(ACC_NATIVE)
					&&
					!containsSpecial(source, classNode, method.instructions)
				) {
					val methDesc = Type.getType(method.desc)
					var descParams = methDesc.getArgumentTypes()
					
					if (config.downcastMergedArguments) {
						for (i in 0 until descParams.size) {
							val t = descParams[i]
							if (t.getSort() == Type.OBJECT) {
								descParams[i] = OBJECT_TYPE
							}
						}
					}
					
					var downcastedDesc = Type.getMethodType(
						methDesc.getReturnType(),
						*descParams
					)
					staticMethods.getOrPut(downcastedDesc.getDescriptor(), { hashSetOf() }).add(Pair(classNode, method))
				}
			}
		}
		
		var classNode: ClassNode? = null
		
		if (staticMethods.isNotEmpty()) {
			val classNamer = CObfuscator.classNamer
			var methodNamer = NameGenerator(rootConfig.remap.methodPrefix)
			
			for ((desc, methods) in staticMethods) {
				val it = methods.shuffled(random).iterator()
				while (it.hasNext()) {
					val (firstClass, firstMethod) = it.next()
					val (secondClass, secondMethod) =
						if (it.hasNext()) it.next() else continue
					
					val highestVersion = 
						config.mergeVersion ?:
						Math.max(firstClass.version, secondClass.version)
					
					val firstStatic = firstMethod.access.hasAccess(ACC_STATIC)
					val secondStatic = secondMethod.access.hasAccess(ACC_STATIC)
					
					if (classNode == null || classNode.methods.size >= config.mergeMaxMethods) {
						methodNamer = NameGenerator(rootConfig.remap.methodPrefix)
						classNode = ClassNode().apply {
							access = ACC_PUBLIC
							version = highestVersion
							name = classNamer.uniqueUntakenClass(source)
							superName = "java/lang/Object"
							source.classes[this.name] = this
						}
					}
					
					val firstDesc = Type.getType(firstMethod.desc)
					val firstParams = firstDesc.getArgumentTypes()
					var firstLocalTypes = Array<Type?>(firstDesc.getArgumentsAndReturnSizes()) { null }
					var i = 0
					for (param in firstParams) {
						if (param.getSort() == Type.OBJECT) {
							firstLocalTypes[i] = param
						}
						i += param.getSize()
					}
					
					val secondDesc = Type.getType(secondMethod.desc)
					val secondParams = secondDesc.getArgumentTypes()
					var secondLocalTypes = Array<Type?>(secondDesc.getArgumentsAndReturnSizes()) { null }
					i = 0
					for (param in secondParams) {
						if (param.getSort() == Type.OBJECT) {
							secondLocalTypes[i] = param
						}
						i += param.getSize()
					}
										
					val newMethod = MethodNode(
						ACC_PUBLIC + ACC_STATIC,
						methodNamer.uniqueRandomString(),
						desc.replace("(", "(Ljava/lang/Object;I"),
						null,
						null
					)
					classNode.methods.add(newMethod)
					
					newMethod.tryCatchBlocks = firstMethod.tryCatchBlocks ?: arrayListOf()
					firstMethod.tryCatchBlocks = null
					
					newMethod.localVariables = incrementLocalVars(firstMethod.localVariables?: arrayListOf(), firstStatic)
					firstMethod.localVariables = null
					
					val baseInt = random.nextInt(Integer.MAX_VALUE - 2)
					val keyInt = random.nextInt(Integer.MAX_VALUE)
					
					val firstStart = newLabel()
					val secondStart = newLabel()
					newMethod.instructions = InsnList().apply {
						val default = newLabel()
						add(default)
						add(VarInsnNode(ILOAD, 1))
						add(ldcInt(keyInt))
						add(IXOR)
						add(
							TableSwitchInsnNode(
								baseInt, baseInt + 1,
								default,
								firstStart, secondStart
							)
						)
						add(secondStart)
						add(
							incAllVarInsn(
								if (config.downcastMergedArguments) {
									castLoads(secondMethod.instructions, secondLocalTypes)
								} else {
									secondMethod.instructions
								},
								secondStatic,
								secondClass.name
							)
						)
						add(firstStart)
						add(
							incAllVarInsn(
								if (config.downcastMergedArguments) {
									castLoads(firstMethod.instructions, firstLocalTypes)
								} else {
									firstMethod.instructions
								},
								firstStatic,
								firstClass.name
							)
						)
					}
					
					firstMethod.instructions = InsnList().apply {
						if (!firstStatic) {
							add(VarInsnNode(ALOAD, 0))
						} else {
							add(ACONST_NULL)
						}
						add(ldcInt(baseInt xor keyInt))
						
						val params = getArgumentTypes(firstMethod.desc)
						var i = if (firstStatic) 0 else (1)
						for (param in params) {
							add(VarInsnNode(getLoadForType(param), i))
							i += 1
							if (param.sort == Type.DOUBLE || param.sort == Type.LONG) {
								i += 1
							}
						}
						add(MethodInsnNode(INVOKESTATIC, classNode.name, newMethod.name, newMethod.desc))
						add(getRetForType(getReturnType(firstMethod.desc)))
					}
					
					secondMethod.instructions = InsnList().apply {
						if (!secondStatic) {
							add(VarInsnNode(ALOAD, 0))
						} else {
							add(ACONST_NULL)
						}
						add(ldcInt((baseInt + 1) xor keyInt))
						
						val params = getArgumentTypes(secondMethod.desc)
						var i = if (secondStatic) 0 else (1)
						for (param in params) {
							add(VarInsnNode(getLoadForType(param), i))
							i += 1
							if (param.sort == Type.DOUBLE || param.sort == Type.LONG) {
								i += 1
							}
						}
						add(MethodInsnNode(INVOKESTATIC, classNode.name, newMethod.name, newMethod.desc))
						add(getRetForType(getReturnType(secondMethod.desc)))
					}
					
					if (secondMethod.tryCatchBlocks != null) newMethod.tryCatchBlocks.addAll(secondMethod.tryCatchBlocks)
					secondMethod.tryCatchBlocks = null
					if (secondMethod.localVariables != null) newMethod.localVariables.addAll(incrementLocalVars(secondMethod.localVariables, secondStatic))
					secondMethod.localVariables = null
					
					stringObfuscator.decryptNode?.let { decryptNode ->
						val modifier = InstructionModifier()
						for (insn in newMethod.instructions) {
							if (insn is MethodInsnNode) {
								if (
									insn.owner == decryptNode.name
									&&
									insn.name == stringObfuscator.fastDecryptMethod.name
								) {
									insn.name = stringObfuscator.decryptMethod.name
									insn.desc = stringObfuscator.decryptMethod.desc
									modifier.prepend(insn, InsnList().apply {
										add(ldcInt(3))
									})
								}
							}
						}
						modifier.apply(newMethod)
					}
				}
			}
		}
	}
	
	private fun <T: MutableCollection<LocalVariableNode>> incrementLocalVars(vars: T, static: Boolean): T {
		val toRemove = arrayListOf<LocalVariableNode>()
		for (localVar in vars) {
			if (localVar.index != 0 || static) {
				localVar.index += 1
			} else {
				//toRemove.add(localVar)
			}
		}
		vars.removeAll(toRemove)
		return vars
	}
	
	private fun incAllVarInsn(insnList: InsnList, static: Boolean, classType: String): InsnList {
		val incAmmount = if (static) 2 else 1
		return InsnList().apply {
			for (insn in insnList) {
				if (insn is VarInsnNode) {
					if (!static && insn.`var` == 0) {
						add(insn)
						
						// Already has a checkcast
						if (insn.next?.opcode == CHECKCAST)
							continue
						
						add(TypeInsnNode(CHECKCAST, classType))
						continue
					} else {
						add(VarInsnNode(insn.opcode, insn.`var` + (incAmmount)))
						continue
					}
				} else if (insn is IincInsnNode) {
					add(IincInsnNode(insn.`var` + (incAmmount), insn.incr))
					continue
				}
				add(insn)
			}
		}
	}
	
	private fun castLoads(insnList: InsnList, args: Array<Type?>): InsnList {
		return InsnList().apply {
			for (insn in insnList) {
				add(insn)
				if (insn is VarInsnNode && insn.opcode == ALOAD) {
					// Already has a checkcast
					if (insn.next?.opcode == CHECKCAST)
						continue
					
					val index = insn.`var`
					if (index < args.size) {
						val desc = args[index]?.getInternalName()
						if (desc != null) {
							add(TypeInsnNode(CHECKCAST, desc))
						}
					}
				}
			}
		}
	}
	
	//fun shuffleArguments(static: Boolean, args: Array<out Type>): Array<Int> {
	//	args.asList().shuffled()
	//	return Array(args.size) {
	//
	//	}
	//}
}
