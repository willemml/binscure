package dev.binclub.binscure.api.transformers

import dev.binclub.binscure.api.TransformerConfiguration

/**
 * @author cookiedragon234 26/Jan/2020
 */
data class IndirectionConfiguration(
	override val enabled: Boolean = false,
	val methodCalls: Boolean = true,
	val fieldAccesses: Boolean = true,
	val variableAccesses: Boolean = false,
	val variableAccessesMinVariables: Int = 5,
	private val exclusions: List<String> = arrayListOf()
): TransformerConfiguration(enabled, exclusions)
