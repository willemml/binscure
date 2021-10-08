package dev.binclub.binscure.classpath

import dev.binclub.binscure.CObfuscator
import dev.binclub.binscure.CObfuscator.random
import dev.binclub.binscure.configuration.ConfigurationManager.rootConfig
import dev.binclub.binscure.utils.DummyHashSet
import dev.binclub.binscure.utils.isExcluded
import dev.binclub.binscure.utils.verifyClass
import dev.binclub.binscure.utils.versionAtLeast
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Field
import java.net.URL
import java.net.URLClassLoader
import java.util.*
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


/**
 * @author cookiedragon234 25/Jan/2020
 */
object ClassPathIO {
	const val HARD_EXCLUDED_CR_FLAGS = ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES

	fun loadInputJar(classSources: ClassSources, file: File) {
		if (rootConfig.useJavaClassloader && rootConfig.sources.size <= 1) {
			addFileToClassPath(file)
		} else {
			if (file.extension == "class") {
				val bytes = file.readBytes()
				val classNode = ClassNode()
				try {
					ClassReader(bytes)
						.accept(classNode, 0)
				} catch (t: Throwable) {
					println("\rError reading class file [${file.name}], skipping")
					t.printStackTrace()
					return
				}
				val hardExcluded = rootConfig.hardExclusions.any { classNode.name.startsWith(it) }

				loadInputClassNode(classSources, file.name, bytes, classNode, hardExcluded)
			} else if (file.extension == "jar" || file.extension == "zip") {
				JarFile(file, false).use { jar ->
					for (entry in jar.entries()) {
						val bytes = jar.getInputStream(entry).readBytes()
						if (!entry.isDirectory && entry.name.endsWith(".class") && !entry.name.endsWith("module-info.class")) {
							val hardExcluded = rootConfig.hardExclusions.any { entry.name.startsWith(it) }
							val classNode = ClassNode()

							try {
								ClassReader(bytes)
									.accept(classNode, if (hardExcluded) HARD_EXCLUDED_CR_FLAGS else ClassReader.SKIP_FRAMES)
							} catch (t: Throwable) {
								println("\rError reading class file [${entry.name}], skipping")
								t.printStackTrace()
								continue
							}

							loadInputClassNode(classSources, entry.name, bytes, classNode, hardExcluded)
						} else if (!entry.isDirectory) {
							classSources.passThrough[entry.name] = bytes
						}
					}
				}
			} else {
				error("Unknown input file extension ${file.extension}")
			}
		}
	}

	fun loadInputClassNode(classSources: ClassSources, name: String, bytes: ByteArray, classNode: ClassNode, hardExcluded: Boolean) {
		val excluded = rootConfig.tExclusions.isExcluded(classNode)

		if (!classNode.versionAtLeast(Opcodes.V1_7) && !excluded && !hardExcluded) {
			if (rootConfig.upgradeVersions) {
				classNode.version = Opcodes.V1_7
			} else {
				println("\rUnsupported <J7 class file ${name}, will not be obfuscated as severely")
			}
		}

		if (!excluded && !hardExcluded) {
			if (rootConfig.shuffleFields) {
				classNode.fields?.shuffle(random)
			}
			if (rootConfig.shuffleMethods) {
				classNode.methods?.shuffle(random)
			}
			if (rootConfig.shuffleClasses) {
				classNode.innerClasses?.shuffle(random)
			}
		}

		if (!hardExcluded) {
			classSources.classes[classNode.name] = classNode
		} else {
			classSources.passThrough[name] = bytes
		}
		classNode.originalName = classNode.name
	}

	fun loadClassPath(classPath: MutableMap<String, ClassNode>, files: Iterable<File>) = loadClassPath(classPath, files.iterator())

	fun loadClassPath(classPath: MutableMap<String, ClassNode>, iterator: Iterator<File>) {
		val files = Stack<File>()
		iterator.forEachRemaining(files::add)

		while (files.isNotEmpty()) {
			val file = files.pop()
			if (file.isDirectory) {
				loadClassPath(classPath, file.listFiles()!!.iterator())
			} else if (file.extension == "jar" || file.extension == "zip") {
				JarFile(file).use {
					for (entry in it.entries()) {
						if (!entry.isDirectory && entry.name.endsWith(".class")) {
							val classNode = ClassNode()
							ClassReader(it.getInputStream(entry).readBytes())
								.accept(classNode, 0)
							classPath[classNode.name] = classNode
						}
					}
				}
			} else {
				println("Unrecognised library type ${file.extension}")
			}
		}
	}

	val emptyClass: ByteArray by lazy {
		ClassWriter(0).also {
			ClassNode().apply {
				version = Opcodes.V1_8
				name = ""
			}.accept(it)
		}.toByteArray()
	}

	private val GARBAGE_CLASS = ByteArray(31).also(random::nextBytes)

	fun writeOutput(classSources: ClassSources, file: File) {
		val fileOut = FileOutputStream(file).buffered()
		JarOutputStream(fileOut).use {
			namesField[it] = DummyHashSet<String>()
			val crc = DummyCRC(0xDEADBEEF)
			if (rootConfig.crasher.enabled && rootConfig.crasher.checksums) {
				crcField[it] = crc
				it.putNextEntry(ZipEntry("â\u3B25\u00d4\ud400®©¯\u00EB\u00A9\u00AE\u008D\u00AA\u002E"))
			}

			var lastPrint = 0L

			fun shouldPrint(): Boolean {
				val now = System.currentTimeMillis()
				return if (now - lastPrint >= 200L) {
					lastPrint = now
					true
				} else false
			}

			val lineChar = rootConfig.getLineChar()

			val passThrough = classSources.passThrough
			var size = passThrough.size
			for ((i, entry) in passThrough.entries.withIndex()) {
				if (shouldPrint() && rootConfig.printProgress) {
					val percentStr = ((i.toFloat() / size) * 100).toInt().toString().padStart(3, ' ')
					print("${lineChar}Writing resources ($percentStr% - $i/${size})".padEnd(100, ' '))
				}
				crc.overwrite = false
				it.putNextEntry(ZipEntry(entry.key))
				it.write(entry.value)
				it.closeEntry()
			}

			if (rootConfig.printProgress) {
				print(rootConfig.getLineChar())
			}

			val classes = classSources.classes
			size = classes.size
			for ((i, classNode) in classes.values.withIndex()) {
				crc.overwrite = false
				
				val excluded = rootConfig.tExclusions.isExcluded(classNode)
				if (shouldPrint() && rootConfig.printProgress) {
					val percentStr = ((i.toFloat() / size) * 100).toInt().toString().padStart(3, ' ')
					print("${lineChar}Writing classes ($percentStr% - $i/${size})".padEnd(100, ' '))
				}
				if (!excluded) {
					crc.overwrite = true
					if (rootConfig.shuffleFields) {
						classNode.fields?.shuffle(random)
					}
					if (rootConfig.shuffleMethods) {
						classNode.methods?.shuffle(random)
					}
					if (rootConfig.shuffleClasses) {
						classNode.innerClasses?.shuffle(random)
					}
				}
				
				val name = "${classNode.name}.class"
				val entry = ZipEntry(name)
				
				if (!excluded && rootConfig.crasher.enabled && rootConfig.crasher.checksums) {
					crc.overwrite = true
					
					it.putNextEntry(ZipEntry(name + "\u0000"))
					it.write(0xDEAD)
					it.write(0xBEEF)
					
					it.putNextEntry(ZipEntry(name))
					it.write(GARBAGE_CLASS)
				}
				
				it.putNextEntry(entry)

				val frames = rootConfig.writeStackMap && classNode.verify
				val arr: ByteArray? = try {
					if (frames) {
						try {
							writeClassNode(true, classSources, classNode)
						} catch (e: Throwable) {							
							System.err.println("${lineChar}Error while writing class ${classNode.niceName} with frames")
							e.printStackTrace()
							writeClassNode(false, classSources, classNode)
						}
					} else {
						writeClassNode(false, classSources, classNode)
					}
				} catch (e: Throwable) {						
					System.err.println("${lineChar}Error while writing class ${classNode.niceName} without frames")
					e.printStackTrace()
					null
				}
				
				if (arr != null) {
					it.write(arr)
					it.closeEntry()
				}
			}

			if (rootConfig.printProgress) {
				print(rootConfig.getLineChar())
			}
		}
	}
	
	fun writeClassNode(stackMap: Boolean, classSources: ClassSources, cn: ClassNode): ByteArray {
		val flags = if (stackMap)
			ClassWriter.COMPUTE_FRAMES
		else
			ClassWriter.COMPUTE_MAXS
		
		val writer = CustomClassWriter(classSources, flags)
		cn.accept(writer)
		return writer.toByteArray()
	}

	val namesField: Field by lazy {
		ZipOutputStream::class.java.getDeclaredField("names").also {
			it.isAccessible = true
		}
	}

	val crcField: Field by lazy {
		ZipOutputStream::class.java.getDeclaredField("crc").also {
			it.isAccessible = true
		}
	}

	val timeField: Field by lazy {
		ZipEntry::class.java.getDeclaredField("csize").also {
			it.isAccessible = true
		}
	}

	val commentField: Field by lazy {
		ZipOutputStream::class.java.getDeclaredField("comment").also {
			it.isAccessible = true
		}
	}

	private class DummyCRC(val crc: Long): CRC32() {
		var overwrite = false

		override fun getValue(): Long {
			return if (overwrite) {
				crc
			} else {
				super.getValue()
			}
		}
	}

	private fun addFileToClassPath(file: File) =
		URLClassLoader::class.java.getDeclaredMethod("addURL", URL::class.java).let {
			it.isAccessible = true
			it(ClassLoader.getSystemClassLoader(), file.toURI().toURL())
		}
}
