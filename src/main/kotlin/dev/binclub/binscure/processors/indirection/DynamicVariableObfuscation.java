package dev.binclub.binscure.processors.indirection;

import dev.binclub.binscure.CObfuscator;
import dev.binclub.binscure.IClassProcessor;
import dev.binclub.binscure.api.transformers.IndirectionConfiguration;
import dev.binclub.binscure.classpath.ClassSources;
import dev.binclub.binscure.utils.ExtensionsKt;
import dev.binclub.binscure.utils.InsnBuilder;
import dev.binclub.binscure.utils.InstructionModifier;
import dev.binclub.binscure.utils.UtilsKt;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Modifier;
import java.util.*;

import static dev.binclub.binscure.configuration.ConfigurationManager.rootConfig;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.BOOLEAN;

@SuppressWarnings("IfStatementWithIdenticalBranches")
public class DynamicVariableObfuscation extends IClassProcessor {
	public DynamicVariableObfuscation(@NotNull ClassSources source) {
		super(source);
	}
	
	@NotNull
	@Override
	public String getProgressDescription() {
		return "Obfuscating local variables";
	}
	
	@NotNull
	@Override
	public IndirectionConfiguration getConfig() {
		return rootConfig.getIndirection();
	}
	
	
	
	private static final String BSM_NAME = "II";
	private static final String BSM_DESC =
		"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;";
	private static final String INDY_DESC = "()Lsun/misc/Unsafe;";
	
	@Override
	public void process(@NotNull ClassSources source, @NotNull Collection<ClassNode> classes, @NotNull Map<String, byte[]> passThrough) {
		if (!getConfig().getEnabled() || !getConfig().getVariableAccesses()) {
			return;
		}
		
		// estimate of the minimum locals a method must have to make this operation worthwhile
		final int MIN_LOCALS = getConfig().getVariableAccessesMinVariables();
		
		ClassNode runtimeClassNode = null;
		Handle bootstrapHandle = null;
		
		for (ClassNode classNode : classes) {
			if (isExcluded(classNode)) continue;
			if (!ExtensionsKt.versionAtLeast(classNode, V1_7)) continue;
			
			for (MethodNode method : classNode.methods) {
				if (isExcluded(classNode, method)) continue;
				if (method.instructions == null || method.maxLocals < MIN_LOCALS) continue;
				// TODO: For some reason this doesnt work with constructors
				// ASM will change the stackframe to label var 0 as "top" rather than "uninitialised_this" :/
				if (method.name.equals("<init>")) continue;
				
				long localsSize = 0;
				Map<Integer, Long> intLocals = new HashMap<>();
				
				boolean added = false;
				int unsafeVarIndex = method.maxLocals;
				int memoryVarIndex = method.maxLocals + 1;
				method.maxLocals += 3; // memoryVarIndex takes up two slots (long)
				
				InstructionModifier mod = new InstructionModifier();
				for (AbstractInsnNode insn : method.instructions) {
					int var;
					IincInsnNode iinc = null;
					if (insn instanceof VarInsnNode) {
						var = ((VarInsnNode) insn).var;
					} else if (insn instanceof IincInsnNode) {
						iinc = (IincInsnNode) insn;
						var = iinc.var;
					} else {
						continue;
					}
					int opcode = insn.getOpcode();
					// cannot apply to these instructions
					if (opcode == ASTORE || opcode == ALOAD || opcode == RET) continue;
					
					// initialise
					if (runtimeClassNode == null) {
						runtimeClassNode = CObfuscator.opaqueRuntimeManager.getClassNode();
						MethodNode bsm = createGetUnsafeBsm();
						runtimeClassNode.methods.add(bsm);
						bootstrapHandle = new Handle(
							H_INVOKESTATIC,
							runtimeClassNode.name,
							BSM_NAME,
							BSM_DESC,
							false
						);
					}
					
					long offset;
					if (!intLocals.containsKey(var)) {
						offset = localsSize;
						intLocals.put(var, offset);
						localsSize += opcodeTypeSize(opcode);
					} else {
						offset = intLocals.get(var);
					}
					
					InsnBuilder replacement = new InsnBuilder();
					
					if (iinc != null) {
						// IINC operation
						// unsafe.setInt(offset, unsafe.getInt(offset) + incr)
						replacement.aload(unsafeVarIndex);
						replacement.lload(memoryVarIndex);
						replacement.ldc(offset);
						replacement.ladd();
						
						replacement.dup2();
						replacement.aload(unsafeVarIndex);
						replacement.dup_x2();
						replacement.pop();
						
						replacement.invokevirtual("sun/misc/Unsafe", "getInt", "(J)I", false);
						
						replacement.ldc(iinc.incr);
						replacement.iadd();
						
						replacement.invokevirtual("sun/misc/Unsafe", "putInt", "(JI)V", false);
					} else {
						boolean store = opcode >= ISTORE && opcode <= DSTORE;
						if (store) {
							// Variable STORE operation
							
							// We need to ensure that the Unsafe object and the offset long are below the argument
							// (this is not necessary for load operations since there is no user provided argument)
							boolean doubleSize = opcode == DSTORE || opcode == LSTORE;
							if (doubleSize) {
								replacement.aload(unsafeVarIndex);
								replacement.dup_x2();
								replacement.pop();
								replacement.lload(memoryVarIndex);
								replacement.ldc(offset);
								replacement.ladd();
								replacement.dup2_x2();
								replacement.pop2();
							} else {
								replacement.aload(unsafeVarIndex);
								replacement.swap();
								replacement.lload(memoryVarIndex);
								replacement.ldc(offset);
								replacement.ladd();
								replacement.dup2_x1();
								replacement.pop2();
							}
						} else {
							// Variable LOAD operation
							replacement.aload(unsafeVarIndex);
							replacement.lload(memoryVarIndex);
							replacement.ldc(offset);
							replacement.ladd();
						}
						
						switch (opcode) {
						case ILOAD:
							replacement.invokevirtual("sun/misc/Unsafe", "getInt", "(J)I", false);
							break;
						case ISTORE:
							replacement.invokevirtual("sun/misc/Unsafe", "putInt", "(JI)V", false);
							break;
						case LLOAD:
							replacement.invokevirtual("sun/misc/Unsafe", "getLong", "(J)J", false);
							break;
						case LSTORE:
							replacement.invokevirtual("sun/misc/Unsafe", "putLong", "(JJ)V", false);
							break;
						case FLOAD:
							replacement.invokevirtual("sun/misc/Unsafe", "getFloat", "(J)F", false);
							break;
						case FSTORE:
							replacement.invokevirtual("sun/misc/Unsafe", "putFloat", "(JF)V", false);
							break;
						case DLOAD:
							replacement.invokevirtual("sun/misc/Unsafe", "getDouble", "(J)D", false);
							break;
						case DSTORE:
							replacement.invokevirtual("sun/misc/Unsafe", "putDouble", "(JD)V", false);
							break;
						default:
							throw new IllegalArgumentException("Unsupported type size from opcode " + opcode);
						}
					}
					
					mod.replace(insn, replacement.getList());
					
					added = true;
				}
				
				if (added) {
					LabelNode tryStart = new LabelNode();
					// need to initialise our local variable that contains Unsafe
					{
						InsnBuilder builder = new InsnBuilder();
						builder.invokedynamic(
							" ",
							INDY_DESC,
							bootstrapHandle
						);
						builder.dup();
						builder.astore(unsafeVarIndex);
						builder.ldc(localsSize);
						builder.invokevirtual("sun/misc/Unsafe", "allocateMemory", "(J)J", false);
						builder.dup2();
						builder.lstore(memoryVarIndex);
						builder.ldc((long)0);
						builder.lcmp();
						builder.ifgt(tryStart);
						
						builder._new("java/lang/NullPointerException");
						builder.dup();
						builder.ldc("Could not allocate memory");
						builder.invokespecial("java/lang/NullPointerException", "<init>", "(Ljava/lang/String;)V", false);
						builder.athrow();
						
						builder.addLabel(tryStart);
						// initialise local variables
						// If the method is static we need to account for var 0 being the this ref
						int varIndex = Modifier.isStatic(method.access) ? 0 : 1;
						Type[] args = Type.getArgumentTypes(method.desc);
						for (Type arg : args) {
							int sort = arg.getSort();
							if (sort >= Type.BOOLEAN && sort <= Type.DOUBLE) {
								// If its not in intlocals then the var is never used and there is no need for this
								if (intLocals.containsKey(varIndex)) {
									long memOffset = intLocals.get(varIndex);
									builder.aload(unsafeVarIndex);
									builder.lload(memoryVarIndex);
									builder.ldc(memOffset);
									builder.ladd();
									switch (sort) {
									case Type.BOOLEAN:
									case Type.CHAR:
									case Type.BYTE:
									case Type.SHORT:
									case Type.INT:
										builder.iload(varIndex);
										builder.invokevirtual("sun/misc/Unsafe", "putInt", "(JI)V", false);
										break;
									case Type.FLOAT:
										builder.fload(varIndex);
										builder.invokevirtual("sun/misc/Unsafe", "putFloat", "(JF)V", false);
										break;
									case Type.LONG:
										builder.lload(varIndex);
										builder.invokevirtual("sun/misc/Unsafe", "putLong", "(JJ)V", false);
										break;
										case Type.DOUBLE:
										builder.dload(varIndex);
										builder.invokevirtual("sun/misc/Unsafe", "putDouble", "(JD)V", false);
										break;
									}
								}
							}
							varIndex += arg.getSize();
						}
						
						mod.insert(builder.getList());
					}
					
					// add equivalent of a `finally` block that frees the memory
					{
						InsnBuilder finallyBlock = new InsnBuilder();
						finallyBlock.aload(unsafeVarIndex);
						finallyBlock.lload(memoryVarIndex);
						finallyBlock.invokevirtual("sun/misc/Unsafe", "freeMemory", "(J)V", false);
						InsnList finallyList = finallyBlock.getList();
						
						for (AbstractInsnNode insn : method.instructions) {
							int opcode = insn.getOpcode();
							if (opcode >= IRETURN && opcode <= RETURN) {
								mod.prepend(insn, ExtensionsKt.clone(finallyList));
							}
						}
						
						
						LabelNode tryEnd = new LabelNode();
						LabelNode handler = new LabelNode();
						
						InsnBuilder exceptionBlock = new InsnBuilder();
						exceptionBlock.addLabel(tryEnd);
						exceptionBlock.aconst_null();
						exceptionBlock.addLabel(handler);
						exceptionBlock.aload(unsafeVarIndex);
						exceptionBlock.swap();
						exceptionBlock.astore(unsafeVarIndex);
						exceptionBlock.lload(memoryVarIndex);
						exceptionBlock.invokevirtual("sun/misc/Unsafe", "freeMemory", "(J)V", false);
						exceptionBlock.aload(unsafeVarIndex);
						exceptionBlock.athrow();
						
						mod.add(exceptionBlock.getList());
						
						method.tryCatchBlocks.add(new TryCatchBlockNode(tryStart, tryEnd, handler, null));
					}
				}
				mod.apply(method);
				if (CObfuscator.INSTANCE.getDEBUG()) {
					try {
						UtilsKt.verifyMethodNode(method);
					} catch (Throwable t) {
						t.printStackTrace();
					}
				}
			}
		}
	}
	
	private long opcodeTypeSize(int opcode) {
		switch (opcode) {
		case ILOAD:
		case ISTORE:
		case IINC:
			return Integer.BYTES;
		case LLOAD:
		case LSTORE:
			return Long.BYTES;
		case FLOAD:
		case FSTORE:
			return Float.BYTES;
		case DLOAD:
		case DSTORE:
			return Double.BYTES;
		default:
			throw new IllegalArgumentException("Unsupported type size from opcode " + opcode);
		}
	}
	
	private MethodNode createGetUnsafeBsm() {
		MethodNode mn = new MethodNode(
			ACC_PUBLIC | ACC_STATIC,
			BSM_NAME,
			BSM_DESC,
			null,
			null
		);
		
		mn.maxLocals = 11;
		mn.maxStack = 4;
		
		InsnBuilder builder = new InsnBuilder();
		// Local Variables:
		// 0  = arg Lookup
		// 1  = arg String: field name
		// 2  = arg MethodType: requested handle type
		
		builder.aload(0);
		builder.ldc(Type.getObjectType("sun/misc/Unsafe"));
		builder.ldc("theUnsafe");
		builder.invokevirtual("java/lang/Class", "getDeclaredField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;", false);
		builder.dup();
		builder.iconst_1();
		builder.invokevirtual("java/lang/reflect/AccessibleObject", "setAccessible", "(Z)V", false);
		builder.invokevirtual(
			"java/lang/invoke/MethodHandles$Lookup",
			"unreflectGetter",
			"(Ljava/lang/reflect/Field;)Ljava/lang/invoke/MethodHandle;",
			false
		);
		builder._new("java/lang/invoke/ConstantCallSite");
		builder.dup_x1();
		builder.swap();
		builder.invokespecial("java/lang/invoke/ConstantCallSite", "<init>", "(Ljava/lang/invoke/MethodHandle;)V", false);
		builder.areturn();
		
		mn.instructions = builder.getList();
		
		return mn;
	}
}
