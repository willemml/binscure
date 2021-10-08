package dev.binclub.binscure.processors.renaming.generation

import dev.binclub.binscure.CObfuscator
import dev.binclub.binscure.classpath.ClassSources
import dev.binclub.binscure.configuration.ConfigurationManager.rootConfig
import dev.binclub.binscure.utils.random
import org.objectweb.asm.tree.ClassNode

/**
 * @author cookiedragon234 22/Jan/2020
 */
open class NameGenerator(val prefix: String = "") {
	companion object {
		val CHARSET = kotlin.run {
			var chars = rootConfig.remap.dictionary.toCharArray()
			if (chars.size < 1) {
				System.err.println("\rWARNING: Empty remap charset")
				chars = "c0123456789abdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray()
			}
			chars
		}
		
		/**
		 * @param index A unique positive integer
		 * @param charset A dictionary to permutate through
		 * @return A unique constants from for the given integer using permutations of the given charset
		 */
		private fun intToStr(index: Int, charset: CharArray): String {
			var i = index
			val radix = charset.size
			val buf = CharArray(65)
			var charPos = 64
			if (i > 0)
				i = -i
			
			while (i <= -radix) {
				buf[charPos--] = charset[(-(i % radix))]
				i /= radix
			}
			buf[charPos] = charset[-i]
			
			return String(buf, charPos, 65 - charPos)
		}
		fun randomFixedSizeString(size: Int): String {
			val charArray = CharArray(size)
			repeat(size) {
				charArray[it] = CHARSET.random(CObfuscator.random)
			}
			return String(charArray)
		}
	}
	protected var index = 0

	open fun uniqueRandomString(): String {
		val out = prefix + intToStr(index, CHARSET)
		index += 1
		return out
	}
	
	fun uniqueUntakenMethodName(cn: ClassNode): String {
		do {
			val out = uniqueRandomString()
			if (cn.methods.none { it.name == out })
				return out
		} while (true)
	}
	fun uniqueUntakenClass(sources: ClassSources): String {
		do {
			val out = uniqueRandomString()
			if (
				!sources.classes.containsKey(out) &&
				!sources.classPath.containsKey(out) &&
				!sources.passThrough.containsKey("$out.class")
			) {
				return out
			}
		} while (true)
	}
}
