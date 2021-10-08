package dev.binclub.binscure.classpath

import dev.binclub.binscure.classpath.tree.ClassNodeTreeEntry
import dev.binclub.binscure.classpath.tree.ClassPathTreeEntry
import dev.binclub.binscure.classpath.tree.ClassTreeEntry
import dev.binclub.binscure.configuration.ConfigurationManager.rootConfig
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.util.jar.JarFile

/**
 * @author cookiedragon234 23/Jan/2020
 */
class ClassSources (classPathInherit: Map<String, ClassNode>) {
	val classPath: MutableMap<String, ClassNode> = HashMap(classPathInherit)
	val classes = mutableMapOf<String, ClassNode>()
	val passThrough = mutableMapOf<String, ByteArray>()
	private val treeEntries = hashMapOf<String, ClassTreeEntry>()
	val hierachy = hashMapOf<String, ClassTree>()
	
	
	private val warnings = mutableSetOf<String>()
	
	fun warn(type: String) = warn(type, Unit)
	
	fun <T> warn(type: String, out: T): T {
		try {
			if (!rootConfig.ignoreClassPathNotFound && type != "give up" && type != "java/lang/YourMum" && warnings.add(type)) {
				System.err.println("\rWARNING: $type was not found in the classpath, may cause sideaffects")
			}
		} catch (t: Throwable) {
			RuntimeException("WARNING: $type was not found in the classpath, may cause sideaffects", t).printStackTrace()
		}
		return out
	}
	
	fun findClass(name: String) = classPath[name] ?: classes[name]
	
	fun getHierarchy(name: String): ClassTree? {
		hierachy[name]?.let { return it }
		
		treeEntries[name]?.let { tree ->
			constructTreeSuperClasses(tree)
			return constructTreeHiearchy(name, tree)
		}
		
		findClass(name)?.let {
			val tree = ClassNodeTreeEntry(it)
			treeEntries[name] = tree
			constructTreeSuperClasses(tree)
			return constructTreeHiearchy(name, tree)
		}
		
		return try {
			val tree = ClassPathTreeEntry(Class.forName(name.replace('/', '.')))
			treeEntries[name] = tree
			constructTreeSuperClasses(tree)
			constructTreeHiearchy(name, tree)
		} catch (ignored: ClassNotFoundException){
			warn(name, null)
		}
	}
	
	fun constructTreeSuperClasses(treeEntry: ClassTreeEntry) {
		for (aSuper in treeEntry.getSuperClasses()) {
			if (findClass(aSuper) == null) {
				try {
					val clazz = Class.forName(aSuper.replace('/', '.'))
					treeEntries[aSuper] = ClassPathTreeEntry(clazz)
				} catch (ignored: Throwable){}
			}
		}
	}
	
	fun reconstructHierarchy() {
		treeEntries.clear()
		hierachy.clear()
		for (classNode in classPath.values) {
			val entry = ClassNodeTreeEntry(classNode)
			treeEntries[classNode.name] = entry
			constructTreeSuperClasses(entry)
		}
		
		for (classNode in classes.values) {
			val entry = ClassNodeTreeEntry(classNode)
			treeEntries[classNode.name] = entry
			constructTreeSuperClasses(entry)
		}
		
		for ((name, entry) in treeEntries) {
			constructTreeHiearchy(name, entry)
		}
	}
	
	private fun constructTreeHiearchy(name: String, entry: ClassTreeEntry): ClassTree {
		val tree = ClassTree(this, entry)
		hierachy[name] = tree
		for (aSuper in entry.getSuperClasses()) {
			val superTree = treeEntries[aSuper]
			if (superTree != null) {
				tree.parents.add(superTree)
			}
		}
		for (entry2 in treeEntries.values) {
			if (entry2.getSuperClasses().contains(name)) {
				tree.children.add(entry2)
			}
		}
		return tree
	}
}
