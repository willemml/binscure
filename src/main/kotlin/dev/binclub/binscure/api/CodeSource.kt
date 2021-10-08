package dev.binclub.binscure.api

import java.io.File

/**
 * @author cook 21/Mar/2021
 */
data class CodeSource(
	val input: File,
	val output: File = File(input.absolutePath.substringBeforeLast('.') + "-obf.jar")
) {
	override fun toString(): String  = """
		|CodeSource
		|   Input: $input
		|   Output: $output
	""".trimMargin()
}
