package dev.binclub.binscure.configuration.exclusions

import java.util.regex.Pattern

object ExclusionFactory {
	fun genExclusion(exclusion: String): ExclusionConfiguration {
		return if (exclusion.startsWith("@")) {
			AnnotationExclusion(exclusion.substring(1))
		} else if (exclusion.startsWith("r#")) {
			RegexExclusion(Pattern.compile(exclusion.substring(2)))
		} else if (exclusion.endsWith(";")) {
			LiteralExclusion(exclusion.substring(0, exclusion.length - 1))
		} else if (exclusion.endsWith("-")) {
			StartExclusion(exclusion.substring(0, exclusion.length - 1), true)
		} else {
			StartExclusion(exclusion, false)
		}
	}
}
