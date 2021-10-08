package dev.binclub.binscure.api.transformers

import dev.binclub.binscure.api.TransformerConfiguration

/**
 * @author cookiedragon234 30/Jun/2020
 */
data class NumberObfuscationConfiguration(
	override val enabled: Boolean = false,
	val floatingPoint: Boolean = true,
	private val exclusions: List<String> = arrayListOf()
): TransformerConfiguration(enabled, exclusions)
