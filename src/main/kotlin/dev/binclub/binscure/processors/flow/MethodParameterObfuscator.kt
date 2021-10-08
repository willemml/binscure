@file:Suppress("NOTHING_TO_INLINE")

package dev.binclub.binscure.processors.flow

import dev.binclub.binscure.CObfuscator
import dev.binclub.binscure.IClassProcessor
import dev.binclub.binscure.classpath.ClassSources
import dev.binclub.binscure.configuration.ConfigurationManager.rootConfig
import dev.binclub.binscure.forClass
import dev.binclub.binscure.forMethod
import dev.binclub.binscure.utils.doubleSize
import dev.binclub.binscure.utils.insnBuilder
import dev.binclub.binscure.utils.InsnBuilder
import dev.binclub.binscure.utils.randomInt
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import java.util.*


class MethodParameterObfuscator (source: ClassSources): IClassProcessor(source) {
	override val progressDescription: String = "Obfuscating method parameters"
	override val config = rootConfig.methodParameter
	
	val META_FACTORY = Handle(
		H_INVOKESTATIC,
		"java/lang/invoke/LambdaMetafactory",
		"metafactory",
		"(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
		false
	)
	val ALT_META_FACTORY = Handle(
		H_INVOKESTATIC,
		"java/lang/invoke/LambdaMetafactory",
		"altMetafactory",
		"(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
		false
	)
	
	
	val methodSecrets: MutableMap<String, Pair<Int, Int>> = HashMap()
	
	fun mnToStr(cn: ClassNode, mn: MethodNode) = cn.name + "." + mn.name + mn.desc
	fun mnToStr(insn: AbstractInsnNode, add: Boolean = false): String {
		if (insn is MethodInsnNode) {
			return insn.owner + "." + insn.name + if (!add) insn.desc else insn.desc.replace(")", "I)")
		} else if (insn is InvokeDynamicInsnNode) {
			if (insn.bsm != META_FACTORY && insn.bsm != ALT_META_FACTORY) {
				throw IllegalArgumentException("Cannot represent non meta indy ${insn.bsm}")
			}
			return mnToStr(insn.bsmArgs[1] as Handle, add)
		}
		throw IllegalStateException("Unsupported insn ${insn.javaClass}")
	}
	
	fun mnToStr(hn: Handle, add: Boolean = false) =
		hn.owner + "." + hn.name + if (!add) hn.desc else hn.desc.replace(")", "I)")
	
	private fun secretIndex(desc: String): Int {
		val args = Type.getArgumentTypes(desc)
		var ourParamIndex = -1 // negate final param append, we need index of it
		for (arg in args) {
			ourParamIndex += if (arg.doubleSize) 2 else 1
		}
		return ourParamIndex
	}
	
	override fun process(
		source: ClassSources,
		classes: MutableCollection<ClassNode>,
		passThrough: MutableMap<String, ByteArray>
	) {
		if (!config.enabled)
			return
		
		if (rootConfig.indirection.enabled)
			println("\rWARNING: Method parameter obfuscation may not work with indirection")
		
		data class ToRemap(
			val thisSecret: Int?, val secretIndex: Int?, val cn: ClassNode, val list: InsnList, val mn: AbstractInsnNode
		)
		
		var highestClassVersion = 0
		
		val methodNodesToRemap = ArrayList<ToRemap>()
		classes.forEach { cn ->
			highestClassVersion = Math.max(highestClassVersion, cn.version)
			cn.methods.forEach { mn ->
				// this methods secret
				val (thisSecret, secretIndex) = methodSecrets[mnToStr(cn, mn)].let {
					(it?.first to it?.second)
				}
				
				mn.instructions?.let { list ->
					for (insn in list) {
						when (insn) {
							is InvokeDynamicInsnNode -> {
								if (insn.bsm == META_FACTORY || insn.bsm == ALT_META_FACTORY) {
									methodNodesToRemap.add(ToRemap(thisSecret, secretIndex, cn, list, insn))
								}
							}
							is MethodInsnNode -> {
								if (
									insn.opcode == INVOKESTATIC
									&&
									insn.name[0] != '<'
									&&
									insn.name != "main"
								) {
									methodNodesToRemap.add(ToRemap(thisSecret, secretIndex, cn, list, insn))
								}
							}
						}
					}
				}
			}
		}
		
		highestClassVersion = rootConfig.flowObfuscation.mergeVersion ?: highestClassVersion
		
		forClass(classes) { cn ->
			if ((cn.access and ACC_ENUM) != 0)
				return@forClass
			
			forMethod(cn) { mn ->
				if (mn.name[0] == '<' || mn.name == "main")
					return@forMethod
				if ((mn.access and (ACC_NATIVE or ACC_ABSTRACT)) != 0)
					return@forMethod
				if ((mn.access and ACC_STATIC) == 0) // so that we dont have to bother with method inheritance
					return@forMethod
				
				val newDesc = mn.desc.replace(")", "I)")
				
				// Since we have added an `I` parameter the method cannot be varargs anymore
				mn.access = mn.access and (ACC_VARARGS xor -1)
				
				// if any of the classes other methods occupy the new desc then cancel, we cant do it
				if (cn.methods.any { it.name == mn.name && it.desc == newDesc })
					return@forMethod
				
				val mnStr = mnToStr(cn, mn)
				
				// take the secret as a parameter
				mn.desc = newDesc
				
				
				// find the local variable table index that we will insert the final parameter into
				val ourParamIndex = secretIndex(mn.desc)
				
				methodSecrets[mnToStr(cn, mn)] = randomInt() to ourParamIndex
				
				// since we've added a new parameter we need to move all local variables up
				
				// modify local variable table
				mn.localVariables?.forEach { lv ->
					if (lv.index >= ourParamIndex) {
						lv.index += 1
					}
				}
				
				// increment local variable references
				for (insn in mn.instructions) {
					when (insn) {
						is VarInsnNode -> {
							if (insn.`var` >= ourParamIndex) {
								insn.`var` += 1
							}
						}
						is IincInsnNode -> {
							if (insn.`var` >= ourParamIndex) {
								insn.`var` += 1
							}
						}
					}
				}
			}
		}
		
		var indyMethods = 0
		var indyClass: ClassNode? = null
		
		methodNodesToRemap.forEach {
			val (thisSecret, secretIndex, cn, list, insn) = it
			
			methodSecrets[mnToStr(insn, true)]?.let { (otherSecret, _) ->
				if (insn is MethodInsnNode) {
					// need to add the parameter
					insn.desc = insn.desc.replace(")", "I)")
					
					// if this method also takes in a secret we can use it to derive the other secret
					val prepend = if (thisSecret != null) {
						insnBuilder {
							iload(secretIndex!!)
							ldc(thisSecret xor otherSecret)
							ixor()
						}
					} else {
						insnBuilder {
							ldc(otherSecret)
						}
					}
					
					list.insertBefore(insn, prepend)
				} else if (insn is InvokeDynamicInsnNode) {
					val args = insn.bsmArgs
					
					val handle = args[1] as Handle
					
					/*
					insn.desc = insn.desc.replace(")", "I)")
					
					args[0] = Type.getMethodType(
						(args[0] as Type)
							.getDescriptor()
							.replace(")", "I)")
					)
					
					args[2] = Type.getMethodType(
						(args[2] as Type)
							.getDescriptor()
							.replace(")", "I)")
					)*/
					
					var nameNum = indyMethods xor 0xDEAD
					indyMethods += 1
					val static = handle.tag == H_INVOKESTATIC
					val proxyMn = MethodNode(
						if (!static) ACC_PUBLIC else (ACC_PUBLIC or ACC_STATIC),
						"$nameNum\$019525$nameNum",
						handle.desc,
						null,
						null
					)
					proxyMn.instructions = insnBuilder {
						var i = 0
						if (!static) {
							aload(0)
							i += 1
						}
						
						val arguments = Type.getArgumentTypes(proxyMn.desc)
						for (arg in arguments) {
							loadType(arg, i)
							i += arg.getSize()
						}
						
						ldc(otherSecret)
						
						val mappedDesc = handle.desc.replace(")", "I)")
						when (handle.tag) {
							H_INVOKEVIRTUAL ->
								invokevirtual(handle.owner, handle.name, mappedDesc, handle.isInterface)
							H_INVOKESTATIC ->
								invokestatic(handle.owner, handle.name, mappedDesc, handle.isInterface)
							H_INVOKESPECIAL ->
								invokespecial(handle.owner, handle.name, mappedDesc, handle.isInterface)
							H_INVOKEINTERFACE ->
								invokeinterface(handle.owner, handle.name, mappedDesc, handle.isInterface)
						}
						
						val retType = Type.getReturnType(proxyMn.desc)
						returnType(retType)
					}
					var inCn: ClassNode = cn
					if (inCn.methods.size >= 0xFF) {
						if (indyClass != null && indyClass!!.methods.size < 0xFF) {
							inCn = indyClass!!
						} else {
							inCn = ClassNode()
							inCn.access = ACC_PUBLIC
							inCn.name = CObfuscator.classNamer.uniqueUntakenClass(source)
							inCn.superName = "java/lang/Object"
							inCn.version = highestClassVersion
							
							indyClass = inCn
							source.classes[inCn.name] = inCn
						}
					}
					inCn.methods.add(proxyMn)
					
					args[1] = Handle(
						if (!static) H_INVOKEVIRTUAL else H_INVOKESTATIC,
						inCn.name,
						proxyMn.name,
						proxyMn.desc,
						(inCn.access and ACC_INTERFACE) != 0
					)
					/*if (insn.bsm == META_FACTORY) {
					} else if (insn.bsm == ALT_META_FACTORY) {
						val args = insn.bsmArgs[0] as Array<Object>
						
						val bridges = args[4] as Array<Type>
						for (i in 0 until bridges.size) {
							bridges[i] = Type.getMethodType(
								bridges[i]
									.getDescriptor()
									.replace(")", "I)")
							)
						}
					}*/
				}				
			}
		}
	}
	
	private fun InsnBuilder.loadType(type: Type, idx: Int) {
		when (type.sort) {
			Type.BOOLEAN -> iload(idx)
			Type.CHAR -> iload(idx)
			Type.BYTE -> iload(idx)
			Type.SHORT -> iload(idx)
			Type.INT -> iload(idx)
			Type.FLOAT -> fload(idx)
			Type.LONG -> lload(idx)
			Type.DOUBLE -> dload(idx)
			Type.ARRAY -> aload(idx)
			Type.OBJECT -> aload(idx)
			else -> throw RuntimeException("Cannot load type $type")
		}
	}
	
	private fun InsnBuilder.returnType(type: Type) {
		when (type.sort) {
			Type.VOID -> _return()
			Type.BOOLEAN -> ireturn()
			Type.CHAR -> ireturn()
			Type.BYTE -> ireturn()
			Type.SHORT -> ireturn()
			Type.INT -> ireturn()
			Type.FLOAT -> ireturn()
			Type.LONG -> lreturn()
			Type.DOUBLE -> dreturn()
			Type.ARRAY -> areturn()
			Type.OBJECT -> areturn()
			else -> throw RuntimeException("Cannot return type $type")
		}
	}
}
