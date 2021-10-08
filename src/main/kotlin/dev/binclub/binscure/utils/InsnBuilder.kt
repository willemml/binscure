@file:Suppress("NOTHING_TO_INLINE", "SpellCheckingInspection", "unused")

package dev.binclub.binscure.utils

import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty

/**
 * @author cookiedragon234 18/May/2020
 */
fun insnBuilder(application: InsnBuilder.() -> Unit): InsnList {
	return InsnBuilder().apply(application).list
}

@Suppress("FunctionName")
class InsnBuilder {
	val list = InsnList()
	
	fun add(insn: AbstractInsnNode) = list.add(insn)
	fun add(list: InsnList) = this.list.add(list)
	
	operator fun InsnList.unaryPlus() = add(this)
	operator fun AbstractInsnNode.unaryPlus() = add(this)
	fun Int.insn() = InsnNode(this)
	
	fun insn(opcode: Int) = +InsnNode(opcode)
	
	fun addLabel(labelNode: LabelNode) = +labelNode
	
	fun _return() = insn(RETURN)
	fun areturn() = insn(ARETURN)
	fun ireturn() = insn(IRETURN)
	fun lreturn() = insn(LRETURN)
	fun freturn() = insn(FRETURN)
	fun dreturn() = insn(DRETURN)
	
	fun aconst_null() = insn(ACONST_NULL)
	
	fun pop() = insn(POP)
	fun pop2() = insn(POP2)
	fun athrow() = insn(ATHROW)
	
	fun ineg() = insn(INEG)
	fun isub() = insn(ISUB)
	fun iadd() = insn(IADD)
	fun imul() = insn(IMUL)
	fun idiv() = insn(IDIV)
	fun ior() = insn(IOR)
	fun iand() = insn(IAND)
	fun ixor() = insn(IXOR)
	fun irem() = insn(IREM)
	
	fun lneg() = insn(LNEG)
	fun lsub() = insn(LSUB)
	fun ladd() = insn(LADD)
	fun lmul() = insn(LMUL)
	fun lor() = insn(LOR)
	fun land() = insn(LAND)
	fun lxor() = insn(LXOR)
	
	fun i2f() = insn(I2F)
	fun i2l() = insn(I2L)
	
	fun lcmp() = insn(LCMP)
	
	fun swap() = insn(SWAP)
	
	fun dup() = insn(DUP)
	fun dup_x1() = insn(DUP_X1)
	fun dup_x2() = insn(DUP_X2)
	fun dup2() = insn(DUP2)
	fun dup2_x1() = insn(DUP2_X1)
	fun dup2_x2() = insn(DUP2_X2)
	
	fun iconst_m1() = insn(ICONST_M1)
	fun iconst_0() = insn(ICONST_0)
	fun iconst_1() = insn(ICONST_1)
	fun iconst_2() = insn(ICONST_2)
	fun iconst_3() = insn(ICONST_3)
	fun iconst_4() = insn(ICONST_4)
	
	fun goto(labelNode: LabelNode) = +JumpInsnNode(GOTO, labelNode)
	fun _goto(labelNode: LabelNode) = goto(labelNode)
	fun ifeq(labelNode: LabelNode) = +JumpInsnNode(IFEQ, labelNode)
	fun ifne(labelNode: LabelNode) = +JumpInsnNode(IFNE, labelNode)
	fun ifle(labelNode: LabelNode) = +JumpInsnNode(IFLE, labelNode)
	fun iflt(labelNode: LabelNode) = +JumpInsnNode(IFLT, labelNode)
	fun ifge(labelNode: LabelNode) = +JumpInsnNode(IFGE, labelNode)
	fun ifgt(labelNode: LabelNode) = +JumpInsnNode(IFGT, labelNode)
	fun if_icmpeq(labelNode: LabelNode) = +JumpInsnNode(IF_ICMPEQ, labelNode)
	fun if_icmpne(labelNode: LabelNode) = +JumpInsnNode(IF_ICMPNE, labelNode)
	fun if_icmplt(labelNode: LabelNode) = +JumpInsnNode(IF_ICMPLT, labelNode)
	fun if_icmpge(labelNode: LabelNode) = +JumpInsnNode(IF_ICMPGE, labelNode)
	fun if_icmpgt(labelNode: LabelNode) = +JumpInsnNode(IF_ICMPGT, labelNode)
	fun if_icmple(labelNode: LabelNode) = +JumpInsnNode(IF_ICMPLE, labelNode)
	fun if_acmpeq(labelNode: LabelNode) = +JumpInsnNode(IF_ACMPEQ, labelNode)
	fun if_acmpne(labelNode: LabelNode) = +JumpInsnNode(IF_ACMPNE, labelNode)
	fun ifnull(labelNode: LabelNode) = +JumpInsnNode(IFNULL, labelNode)
	fun ifnonnull(labelNode: LabelNode) = +JumpInsnNode(IFNONNULL, labelNode)
	
	fun astore(`var`: Int) = +VarInsnNode(ASTORE, `var`)
	fun aload(`var`: Int) = +VarInsnNode(ALOAD, `var`)
	fun iload(`var`: Int) = +VarInsnNode(ILOAD, `var`)
	fun istore(`var`: Int) = +VarInsnNode(ISTORE, `var`)
	fun fload(`var`: Int) = +VarInsnNode(FLOAD, `var`)
	fun fstore(`var`: Int) = +VarInsnNode(FSTORE, `var`)
	fun lload(`var`: Int) = +VarInsnNode(LLOAD, `var`)
	fun lstore(`var`: Int) = +VarInsnNode(LSTORE, `var`)
	fun dload(`var`: Int) = +VarInsnNode(DLOAD, `var`)
	fun dstore(`var`: Int) = +VarInsnNode(DSTORE, `var`)
	
	fun iinc(`var`: Int, incr: Int) = +IincInsnNode(`var`, incr)
	
	fun aastore() = insn(AASTORE)
	fun aaload() = insn(AALOAD)
	fun castore() = insn(CASTORE)
	fun caload() = insn(CALOAD)
	
	fun arraylength() = insn(ARRAYLENGTH)
	
	fun invokestatic(owner: String, name: String, desc: String, `interface`: Boolean = false)
		= +MethodInsnNode(INVOKESTATIC, owner, name, desc, `interface`)
	fun invokestatic(kClass: KClass<*>, kFunction: KFunction<*>)
		= +MethodInsnNode(INVOKESTATIC, kClass.internalName, kFunction.name, kFunction.descriptor)
	fun invokevirtual(owner: String, name: String, desc: String, `interface`: Boolean = false)
		= +MethodInsnNode(INVOKEVIRTUAL, owner, name, desc, `interface`)
	fun invokevirtual(kClass: KClass<*>, kFunction: KFunction<*>)
		= +MethodInsnNode(INVOKEVIRTUAL, kClass.internalName, kFunction.name, kFunction.descriptor)
	fun invokespecial(owner: String, name: String, desc: String, `interface`: Boolean = false)
		= +MethodInsnNode(INVOKESPECIAL, owner, name, desc, `interface`)
	fun invokespecial(kClass: KClass<*>, kFunction: KFunction<*>)
		= +MethodInsnNode(INVOKESPECIAL, kClass.internalName, kFunction.name, kFunction.descriptor)
	fun invokespecial(kClass: KClass<*>, name: String, desc: String, `interface`: Boolean = false)
		= +MethodInsnNode(INVOKESPECIAL, kClass.internalName, name, desc, `interface`)
	fun invokeinterface(owner: String, name: String, desc: String, `interface`: Boolean = false)
		= +MethodInsnNode(INVOKEINTERFACE, owner, name, desc, `interface`)
	fun invokeinterface(kClass: KClass<*>, kFunction: KFunction<*>)
		= +MethodInsnNode(INVOKEINTERFACE, kClass.internalName, kFunction.name, kFunction.descriptor)
	
	fun invokedynamic(name: String, descriptor: String, bootstrapMethodHandle: Handle, vararg bootstrapMethodArguments: Any)
		= +InvokeDynamicInsnNode(name, descriptor, bootstrapMethodHandle, *bootstrapMethodArguments)
	
	
	fun getstatic(owner: String, name: String, desc: String)
		= +FieldInsnNode(GETSTATIC, owner, name, desc)
	fun getstatic(kClass: KClass<*>, kField: KProperty<*>)
		= +FieldInsnNode(GETSTATIC, kClass.internalName, kField.name, kField.descriptor)
	fun getfield(owner: String, name: String, desc: String)
		= +FieldInsnNode(GETFIELD, owner, name, desc)
	fun getfield(kClass: KClass<*>, kField: KProperty<*>)
		= +FieldInsnNode(GETFIELD, kClass.internalName, kField.name, kField.descriptor)
	
	fun putstatic(owner: String, name: String, desc: String)
		= +FieldInsnNode(PUTSTATIC, owner, name, desc)
	fun putstatic(kClass: KClass<*>, kField: KProperty<*>)
		= +FieldInsnNode(PUTSTATIC, kClass.internalName, kField.name, kField.descriptor)
	fun putfield(owner: String, name: String, desc: String)
		= +FieldInsnNode(PUTFIELD, owner, name, desc)
	fun putfield(kClass: KClass<*>, kField: KProperty<*>)
		= +FieldInsnNode(PUTFIELD, kClass.internalName, kField.name, kField.descriptor)
	
	fun checkcast(type: String) = +TypeInsnNode(CHECKCAST, type)
	
	fun new(type: String) = +TypeInsnNode(NEW, type)
	fun _new(type: String) = new(type)
	fun new(type: KClass<*>) = +TypeInsnNode(NEW, type.internalName)
	fun newboolarray() = newarray(T_BOOLEAN)
	fun newchararray() = newarray(T_CHAR)
	fun newbytearray() = newarray(T_BYTE)
	fun newshortarray()  = newarray(T_SHORT)
	fun newintarray() = newarray(T_INT)
	fun newfloatarray() = newarray(T_FLOAT)
	fun newdoublearray() = newarray(T_DOUBLE)
	fun newlongarray() = newarray(T_LONG)
	fun newarray(type: Int) = +IntInsnNode(NEWARRAY, type)
	fun anewarray(desc: String) = +TypeInsnNode(ANEWARRAY, desc)
	
	fun ldc(int: Int) = +ldcInt(int)
	fun ldc(float: Float) = +ldcFloat(float)
	fun ldc(long: Long) = +ldcLong(long)
	fun ldc(double: Double) = +ldcDouble(double)
	fun ldc(string: String) = +LdcInsnNode(string)
	fun ldc(type: Type) = +LdcInsnNode(type)
	fun ldc(handle: Handle) = +LdcInsnNode(handle)
	fun ldc_unsafe(constant: Any) = +LdcInsnNode(constant)
	
	fun tableswitch(baseNumber: Int, dflt: LabelNode, vararg targets: LabelNode) = +constructTableSwitch(baseNumber, dflt, *targets)
	fun lookupswitch(defaultLabel: LabelNode, lookup: Array<Pair<Int, LabelNode>>) = +constructLookupSwitch(defaultLabel, lookup)

}
