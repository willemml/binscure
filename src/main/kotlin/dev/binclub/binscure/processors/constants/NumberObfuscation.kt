package dev.binclub.binscure.processors.constants

import dev.binclub.binscure.IClassProcessor
import dev.binclub.binscure.api.transformers.NumberObfuscationConfiguration
import dev.binclub.binscure.classpath.ClassSources
import dev.binclub.binscure.configuration.ConfigurationManager.rootConfig
import dev.binclub.binscure.utils.add
import dev.binclub.binscure.utils.internalName
import dev.binclub.binscure.utils.*
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.MethodInsnNode

/**
 * @author cookiedragon234 30/Jan/2020
 */
class NumberObfuscation(source: ClassSources): IClassProcessor(source) {
	override val progressDescription: String
		get() = "Obfuscating number constants"
	override val config: NumberObfuscationConfiguration
		get() = rootConfig.numberObfuscation
	
	override fun process(
		source: ClassSources,
		classes: MutableCollection<ClassNode>,
		passThrough: MutableMap<String, ByteArray>
	) {
		if (!config.enabled)
			return
		
		for (classNode in classes) {
			if (isExcluded(classNode))
				continue
			
			for (method in classNode.methods) {
				if (isExcluded(classNode, method) || method.instructions == null)
					continue
				
				val modifier = InstructionModifier()
				for (insn in method.instructions) {
					if (isNumberLdc(insn)) {
						when (val num = getNumFromLdc(insn)) {
							is Int -> obfInt(modifier, insn, num)
							is Long -> obfLong(modifier, insn, num)
							is Double -> obfDouble(modifier, insn, num)
							is Float -> obfFloat(modifier, insn, num)
						}
					}
				}
				modifier.apply(method)
			}
		}
	}
	
	private fun obfFloat(modifier: InstructionModifier, insn: AbstractInsnNode, num: Float) {
		if (!config.floatingPoint)
			return
		
		val firstRand = random.nextFloat() * Float.MAX_VALUE
		val numAsInt = java.lang.Float.floatToIntBits(num)
		val randAsInt = java.lang.Float.floatToIntBits(firstRand)
		val list = InsnList().apply {
			add(ldcInt(numAsInt))
			/*
			if (random.nextBoolean()) {
				add(ldcInt(randAsInt))
				add(ldcInt(numAsInt xor randAsInt))
			} else {
				add(ldcInt(numAsInt xor randAsInt))
				add(ldcInt(randAsInt))
			}
			add(IXOR)*/
			add(MethodInsnNode(INVOKESTATIC, java.lang.Float::class.internalName, "intBitsToFloat", "(I)F"))
		}
		modifier.replace(insn, list)
	}
	
	private fun obfDouble(modifier: InstructionModifier, insn: AbstractInsnNode, num: Double) {
		if (!config.floatingPoint)
			return
		
		val firstRand = random.nextDouble() * Double.MAX_VALUE
		val numAsLong = java.lang.Double.doubleToLongBits(num)
		val randAsLong = java.lang.Double.doubleToLongBits(firstRand)
		val list = InsnList().apply {
			add(ldcLong(numAsLong))
			/*
			if (random.nextBoolean()) {
				add(ldcLong(randAsLong))
				add(ldcLong(numAsLong xor randAsLong))
			} else {
				add(ldcLong(numAsLong xor randAsLong))
				add(ldcLong(randAsLong))
			}
			add(LXOR)*/
			add(MethodInsnNode(INVOKESTATIC, java.lang.Double::class.internalName, "longBitsToDouble", "(J)D"))
		}
		modifier.replace(insn, list)
	}
	
	private fun obfInt(modifier: InstructionModifier, insn: AbstractInsnNode, num: Int) {
		val firstRand = randomInt()
		val list = InsnList().apply {
			add(ldcLong(firstRand.toLong()))
			add(L2I)
			add(ldcInt(firstRand xor num))
			add(IXOR)
		}
		modifier.replace(insn, list)
	}
	
	private fun obfLong(modifier: InstructionModifier, insn: AbstractInsnNode, num: Long) {
		val firstRand = randomInt()
		val list = InsnList().apply {
			add(ldcInt(firstRand))
			add(I2L)
			add(ldcLong(firstRand.toLong() xor num))
			add(LXOR)
		}
		modifier.replace(insn, list)
	}
}
