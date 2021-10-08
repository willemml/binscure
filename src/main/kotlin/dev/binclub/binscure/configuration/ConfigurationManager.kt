package dev.binclub.binscure.configuration

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.PropertySource
import dev.binclub.binscure.api.RootConfiguration
import java.io.File

/**
 * @author cookiedragon234 25/Jan/2020
 */
object ConfigurationManager {
	lateinit var rootConfig: RootConfiguration
	fun parse(configFile: File): RootConfiguration {
		val source = PropertySource.file(configFile)
		rootConfig = ConfigLoader.Builder()
			.addSource(source)
			.build()
			.loadConfigOrThrow<RootConfiguration>(arrayListOf<String>())
		return rootConfig
	}
}
