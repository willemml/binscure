package dev.binclub.binscure.api.transformers

import dev.binclub.binscure.api.TransformerConfiguration

/**
 * @author cookiedragon234 26/Jan/2020
 */
data class StringObfuscationConfiguration(
	override val enabled: Boolean = false,
	val maxLength: Int = 10000,
	private val exclusions: List<String> = arrayListOf()
): TransformerConfiguration(enabled, exclusions)
