package dev.binclub.binscure.processors.indirection;

import dev.binclub.binscure.CObfuscator;
import dev.binclub.binscure.IClassProcessor;
import dev.binclub.binscure.api.transformers.IndirectionConfiguration;
import dev.binclub.binscure.classpath.ClassSources;
import dev.binclub.binscure.utils.*;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.Collection;
import java.util.Map;

import static dev.binclub.binscure.configuration.ConfigurationManager.rootConfig;
import static org.objectweb.asm.Opcodes.*;

public class DynamicFieldObfuscation extends IClassProcessor {
	public DynamicFieldObfuscation(@NotNull ClassSources source) {
		super(source);
	}

	@NotNull
	@Override
	public String getProgressDescription() {
		return "Obfuscating Field Accesses";
	}

	@NotNull
	@Override
	public IndirectionConfiguration getConfig() {
		return rootConfig.getIndirection();
	}


	private static final String BSM_NAME = "lI";
	private static final String BSM_DESC =
		"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/invoke/CallSite;";

	@Override
	public void process(@NotNull ClassSources source, @NotNull Collection<ClassNode> classes, @NotNull Map<String, byte[]> passThrough) {
		if (!getConfig().getEnabled() || !getConfig().getFieldAccesses()) {
			return;
		}

		ClassNode runtimeClassNode = null;
		Handle bootstrapHandle = null;

		for (ClassNode classNode : classes) {
			if (isExcluded(classNode)) continue;
			if (!ExtensionsKt.versionAtLeast(classNode, V1_7)) continue;
			boolean interfaceClass = (classNode.access & ACC_INTERFACE) != 0;

			for (MethodNode method : classNode.methods) {
				if (isExcluded(classNode, method)) continue;
				if (method.instructions == null) continue;
				// We can't do virtual field accesses until we have initialised the class
				// The JVM allows field ops like GETFIELD/PUTFIELD on uninitialized classes but
				// *NOT* method calls incl indies
				// We could keep track of init state and only do dynamic field accesses once initialized,
				// but this is too complicated
				if (method.name.equals("<init>")) continue;
				// JVM has special case to allow PUTFIELD of an interfaces' static
				// final fields within the <clinit>, however it will not extend
				// this to method handles, as such we cannot make interface init
				// field accesses dynamic
				if (interfaceClass && method.name.equals("<clinit>")) continue;

				InstructionModifier mod = new InstructionModifier();
				for (AbstractInsnNode insn : method.instructions) {
					if (!(insn instanceof FieldInsnNode)) continue;
					int opcode = insn.getOpcode();
					if (opcode < GETSTATIC || opcode > PUTFIELD) continue;

					// initialise
					if (runtimeClassNode == null) {
						runtimeClassNode = CObfuscator.opaqueRuntimeManager.getClassNode();
						MethodNode bsm = createBsm();
						runtimeClassNode.methods.add(bsm);
						bootstrapHandle = new Handle(
							H_INVOKESTATIC,
							runtimeClassNode.name,
							BSM_NAME,
							BSM_DESC,
							false
						);
					}

					FieldInsnNode fieldNode = (FieldInsnNode) insn;

					String owner = fieldNode.owner;
					String name = fieldNode.name;
					String fieldDesc = fieldNode.desc;

					String desc = null;
					boolean virtual = false;
					switch (opcode) {
					case GETSTATIC:
						desc = "()" + fieldDesc;
						break;
					case PUTSTATIC:
						desc = "(" + fieldDesc + ")V";
						break;
					case GETFIELD:
						virtual = true;
						desc = "(L" + owner + ";)" + fieldDesc;
						break;
					case PUTFIELD:
						virtual = true;
						desc = "(L" + owner + ";" + fieldDesc + ")V";
						break;
					}

					String encryptKey = classNode.name.replace("/", ".") + method.name;
					name = encryptStr(encryptKey, name);
					owner = encryptStr(encryptKey, owner.replace("/", "."));

					InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode(
						" ",
						desc,
						bootstrapHandle,
						Type.getObjectType(classNode.name),
						owner,
						virtual ? "net.minecraft.client.model.ModelEnderman" : "",
						name
					);

					mod.replace(insn, indy);
				}
				mod.apply(method);
			}
		}
	}

	private String encryptStr(String key, String value) {
		int keyLength = key.length();
		int length = value.length();
		StringBuilder out = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			char newC = (char) ((value.charAt(i) ^ 0x2468ACE1) ^ key.charAt(i % keyLength));
			out.append(newC);
		}
		return out.toString();
	}

	private InsnList decryptList() {
		InsnBuilder decryptBuilder = new InsnBuilder();
		// input = var 11
		// key = var 8
		// key length = var 9
		decryptBuilder.astore(11);

		decryptBuilder._new("java/lang/StringBuilder");
		decryptBuilder.dup();
		decryptBuilder.aload(11);
		decryptBuilder.invokevirtual("java/lang/String", "length", "()I", false);
		decryptBuilder.dup();
		decryptBuilder.istore(7);
		decryptBuilder.invokespecial("java/lang/StringBuilder", "<init>", "(I)V", false);
		decryptBuilder.astore(6);

		LabelNode loopStart = new LabelNode();
		LabelNode loopEnd = new LabelNode();
		decryptBuilder.iconst_0();
		decryptBuilder.istore(10);

		decryptBuilder.addLabel(loopStart);
		decryptBuilder.iload(10);
		decryptBuilder.iload(7);
		decryptBuilder.if_icmpge(loopEnd);
		decryptBuilder.aload(11);
		decryptBuilder.iload(10);
		decryptBuilder.invokevirtual("java/lang/String", "charAt", "(I)C", false);
		decryptBuilder.ldc(0x2468ACE1);
		decryptBuilder.ixor();
		decryptBuilder.aload(8);
		decryptBuilder.iload(10);
		decryptBuilder.iload(9);
		decryptBuilder.irem();
		decryptBuilder.caload();
		decryptBuilder.ixor();
		decryptBuilder.aload(6);
		decryptBuilder.swap();
		decryptBuilder.invokevirtual("java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;", false);
		decryptBuilder.astore(6);
		decryptBuilder.iinc(10, 1);
		decryptBuilder._goto(loopStart);

		decryptBuilder.addLabel(loopEnd);
		decryptBuilder.iload(10);
		decryptBuilder.iload(7);
		decryptBuilder.isub();
		decryptBuilder.istore(10);

		decryptBuilder.aload(6);
		decryptBuilder.invokevirtual("java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
		decryptBuilder.aconst_null();
		decryptBuilder.astore(6);

		return decryptBuilder.getList();
	}

	private InsnList getDecryptKey() {
		InsnBuilder getDecryptKey = new InsnBuilder();

		// caller = Thread.currentThread().getStackTrace()
		getDecryptKey.invokestatic("java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
		getDecryptKey.invokevirtual("java/lang/Thread", "getStackTrace", "()[Ljava/lang/StackTraceElement;", false);
		getDecryptKey.dup();
		getDecryptKey.astore(8);
		getDecryptKey.arraylength();
		getDecryptKey.istore(9);
		getDecryptKey.iconst_3();
		getDecryptKey.istore(10);

		// caller = Thread
		LabelNode loopStart = new LabelNode();
		LabelNode loopEnd = new LabelNode();
		getDecryptKey.addLabel(loopStart);
		getDecryptKey.iload(10);
		getDecryptKey.iload(9);
		getDecryptKey.if_icmpeq(loopEnd);
		getDecryptKey.aload(8);
		getDecryptKey.iload(10);
		getDecryptKey.aaload();
		getDecryptKey.invokevirtual("java/lang/StackTraceElement", "getClassName", "()Ljava/lang/String;", false);
		getDecryptKey.ldc("java.");
		getDecryptKey.invokevirtual("java/lang/String", "startsWith", "(Ljava/lang/String;)Z", false);
		getDecryptKey.ifeq(loopEnd);
		getDecryptKey.iinc(10, 1);
		getDecryptKey._goto(loopStart);
		getDecryptKey.addLabel(loopEnd);

		// caller = caller[i];
		getDecryptKey.aload(8);
		getDecryptKey.iload(10);
		getDecryptKey.dup();
		getDecryptKey.dup();
		getDecryptKey.isub();
		getDecryptKey.istore(10);
		getDecryptKey.aaload();
		getDecryptKey.astore(8);
		// new StringBuilder()
		getDecryptKey._new("java/lang/StringBuilder");
		getDecryptKey.dup();
		getDecryptKey.invokespecial("java/lang/StringBuilder", "<init>", "()V", false);
		// append(caller.getClassName)
		// append(caller.getMethodName)
		getDecryptKey.aload(8);
		getDecryptKey.invokevirtual("java/lang/StackTraceElement", "getClassName", "()Ljava/lang/String;", false);
		getDecryptKey.invokevirtual("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
		getDecryptKey.aload(8);
		getDecryptKey.invokevirtual("java/lang/StackTraceElement", "getMethodName", "()Ljava/lang/String;", false);
		getDecryptKey.invokevirtual("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
		// caller = sb
		getDecryptKey.dup();
		// new char[caller.length]
		getDecryptKey.invokevirtual("java/lang/StringBuilder", "length", "()I", false);
		getDecryptKey.dup();
		getDecryptKey.dup();
		getDecryptKey.istore(9);
		getDecryptKey.newarray(T_CHAR);
		getDecryptKey.dup();
		getDecryptKey.astore(8);
		// caller = caller.getChars(0, length, char[], 0);
		getDecryptKey.iconst_0();
		getDecryptKey.dup_x2();
		getDecryptKey.invokevirtual("java/lang/StringBuilder", "getChars", "(II[CI)V", false);

		return getDecryptKey.getList();
	}

	private MethodNode createBsm() {
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
		// 3  = arg Class: caller
		// 4  = arg String: owner
		// 5  = arg String: empty if virtual
		// 6  = loc CallSite: return value
		// 7  = loc int: method type parameter count
		// 8  = loc char[]: caller
		// 9  = loc int: caller size
		// 10 = loc int: loop counter
		// 11 = loc String: string to decrypt

		LabelNode getLbl = new LabelNode();
		LabelNode end = new LabelNode();

		LabelNode try1 = new LabelNode();
		LabelNode try2 = new LabelNode();
		LabelNode try3 = new LabelNode();
		LabelNode try4 = new LabelNode();
		LabelNode try5 = new LabelNode();
		LabelNode try6 = new LabelNode();
		LabelNode end1 = new LabelNode();
		LabelNode end2 = new LabelNode();
		LabelNode end3 = new LabelNode();
		LabelNode end4 = new LabelNode();
		LabelNode end5 = new LabelNode();
		LabelNode end6 = new LabelNode();
		LabelNode handler = new LabelNode();

		mn.tryCatchBlocks.add(new TryCatchBlockNode(try1, end1, handler, "java/lang/ArithmeticException"));
		mn.tryCatchBlocks.add(new TryCatchBlockNode(try2, end2, handler, "java/lang/ArithmeticException"));
		mn.tryCatchBlocks.add(new TryCatchBlockNode(try3, end3, handler, "java/lang/ArithmeticException"));
		mn.tryCatchBlocks.add(new TryCatchBlockNode(try4, end4, handler, "java/lang/ArithmeticException"));
		mn.tryCatchBlocks.add(new TryCatchBlockNode(try5, end5, handler, "java/lang/ArithmeticException"));
		mn.tryCatchBlocks.add(new TryCatchBlockNode(try6, end6, handler, "java/lang/ArithmeticException"));

		{
			builder.addLabel(try1);
			builder.aload(6);
			builder.astore(1);

			builder.aconst_null();
			builder.astore(11);
			builder.addLabel(try2);

			// 5 = 5.isEmpty() ? null : 5;
			builder.aload(5);
			builder.dup();
			builder.invokevirtual("java/lang/String", "isEmpty", "()Z", false);
			LabelNode strEnd = new LabelNode();
			LabelNode strEmpty = new LabelNode();
			builder.ifne(strEmpty);
			builder._goto(strEnd);
			builder.addLabel(strEmpty);
			builder.pop();
			builder.aconst_null();
			builder.addLabel(strEnd);
			builder.astore(5);

			builder.aconst_null();
			builder.astore(6);
			// methodType.parameterCount()
			builder.aload(2);
			builder.invokevirtual("java/lang/invoke/MethodType", "parameterCount", "()I", false);

			// paramCount -= (owner != null ? 1 : 0)
			// if (paramCount == 0) get else set
			builder.aload(5);
			LabelNode ownerExit = new LabelNode();
			LabelNode ownerNull = new LabelNode();
			builder.ifnull(ownerNull);
			builder.iconst_1();
			builder._goto(ownerExit);
			builder.addLabel(ownerNull);
			builder.iconst_0();
			builder.addLabel(ownerExit);
			builder.isub();
			builder.ifeq(getLbl);
		}

		LabelNode setVirtual = new LabelNode();
		{
			// Set instructions
			builder.aload(5);
			builder.ifnonnull(setVirtual);

			{
				// PUTSTATIC
				builder.aload(0); // lookup
				// field owner
				builder.addLabel(try4);
				// caller = Class.forName(owner, true, caller.getClassLoader())
				builder.add(getDecryptKey());
				builder.aload(4);
				builder.add(decryptList());
				builder.iconst_1();
				builder.aload(3);
				builder.addLabel(try5);
				builder.invokevirtual("java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;", false);
				builder.invokestatic("java/lang/Class", "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;", false);
				builder.addLabel(end4);

				// field name
				builder.aload(1);
				builder.add(decryptList());
				// field type
				builder.aload(2);
				builder.iconst_0();
				builder.invokevirtual("java/lang/invoke/MethodType", "parameterType", "(I)Ljava/lang/Class;", false);
				// lookup.findStaticSetter
				builder.invokevirtual(
					"java/lang/invoke/MethodHandles$Lookup",
					"findStaticSetter",
					"(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;",
					false
				);
				// new ConstantCallSite(methodHandle)
				builder._new("java/lang/invoke/ConstantCallSite");
				builder.addLabel(try3);
				builder.dup_x1();
				builder.swap();
				builder.invokespecial("java/lang/invoke/ConstantCallSite", "<init>", "(Ljava/lang/invoke/MethodHandle;)V", false);
				builder.astore(6);
				builder.iconst_3();
				builder.iload(10);
				builder.idiv();
				builder.istore(10);
				builder._goto(end);
			}
			{
				// PUTFIELD
				builder.addLabel(setVirtual);
				builder.add(getDecryptKey());
				builder.aload(0); // lookup
				// field owner
				builder.aload(2);
				builder.iconst_0();
				builder.invokevirtual("java/lang/invoke/MethodType", "parameterType", "(I)Ljava/lang/Class;", false);
				// field name
				builder.aload(1);
				builder.add(decryptList());
				// field type
				builder.aload(2);
				builder.iconst_1();
				builder.invokevirtual("java/lang/invoke/MethodType", "parameterType", "(I)Ljava/lang/Class;", false);
				// lookup.findSetter
				builder.invokevirtual(
					"java/lang/invoke/MethodHandles$Lookup",
					"findSetter",
					"(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;",
					false
				);
				// new ConstantCallSite(methodHandle)
				builder._new("java/lang/invoke/ConstantCallSite");
				builder.dup_x1();
				builder.swap();
				builder.addLabel(try6);
				builder.invokespecial("java/lang/invoke/ConstantCallSite", "<init>", "(Ljava/lang/invoke/MethodHandle;)V", false);
				builder.astore(6);
				builder.iconst_3();
				builder.iload(10);
				builder.idiv();
				builder.istore(10);
				builder._goto(end);
				builder.addLabel(end5);
			}
		}
		LabelNode getVirtual = new LabelNode();
		{
			// Get instructions
			builder.addLabel(getLbl);
			builder.aload(5);
			builder.ifnonnull(getVirtual);

			{
				builder.add(getDecryptKey());
				// GETSTATIC
				builder.aload(0); // lookup
				// field owner
				// caller = Class.forName(owner, true, caller.getClassLoader())
				builder.aload(4);
				builder.add(decryptList());
				builder.iconst_1();
				builder.aload(3);
				builder.invokevirtual("java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;", false);
				builder.invokestatic("java/lang/Class", "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;", false);
				// field name
				builder.aload(1);
				builder.add(decryptList());
				// field type
				builder.aload(2);
				builder.invokevirtual("java/lang/invoke/MethodType", "returnType", "()Ljava/lang/Class;", false);
				// lookup.findStaticGetter
				builder.invokevirtual(
					"java/lang/invoke/MethodHandles$Lookup",
					"findStaticGetter",
					"(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;",
					false
				);
				// new ConstantCallSite(methodHandle)
				builder._new("java/lang/invoke/ConstantCallSite");
				builder.dup_x1();
				builder.swap();
				builder.invokespecial("java/lang/invoke/ConstantCallSite", "<init>", "(Ljava/lang/invoke/MethodHandle;)V", false);
				builder.astore(6);
				builder.iconst_3();
				builder.iload(10);
				builder.idiv();
				builder.istore(10);
				builder._goto(end);
				builder.addLabel(end2);
			}
			{
				// GETFIELD
				builder.addLabel(getVirtual);
				builder.aload(0); // lookup
				// field owner
				builder.aload(2);
				builder.iconst_0();
				builder.add(getDecryptKey());
				builder.invokevirtual("java/lang/invoke/MethodType", "parameterType", "(I)Ljava/lang/Class;", false);
				// field name
				builder.aload(1);
				builder.add(decryptList());
				// field type
				builder.aload(2);
				builder.invokevirtual("java/lang/invoke/MethodType", "returnType", "()Ljava/lang/Class;", false);
				// lookup.findGetter
				builder.invokevirtual(
					"java/lang/invoke/MethodHandles$Lookup",
					"findGetter",
					"(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;",
					false
				);
				// new ConstantCallSite(methodHandle)
				builder._new("java/lang/invoke/ConstantCallSite");
				builder.dup_x1();
				builder.swap();
				builder.invokespecial("java/lang/invoke/ConstantCallSite", "<init>", "(Ljava/lang/invoke/MethodHandle;)V", false);
				builder.astore(6);
				builder.iconst_3();
				builder.iload(10);
				builder.idiv();
				builder.istore(10);
				builder._goto(end);
				builder.addLabel(end6);
			}
		}

		// End
		LabelNode gotoVirtual = new LabelNode();
		builder.addLabel(end);
		builder.aconst_null();
		builder.iload(10);
		builder.iflt(gotoVirtual);
		builder.pop();
		builder.aload(6);
		builder.aconst_null();
		builder.astore(6);
		builder.iload(10);
		builder.ifne(gotoVirtual);
		builder._goto(handler);
		builder.addLabel(gotoVirtual);
		builder.pop();
		builder._goto(setVirtual);

		builder.addLabel(handler);
		builder.pop();
		builder.aload(6);
		builder.ifnull(getVirtual);
		builder.aload(6);
		builder.checkcast("java/lang/invoke/CallSite");
		builder.areturn();
		builder.addLabel(end1);
		builder.addLabel(end3);

		mn.instructions = builder.getList();

		return mn;
	}
}
