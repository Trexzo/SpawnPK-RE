import spk.local.WidgetActionClientRequest
import spk.plugin.api.Plugin
import spk.plugin.api.PluginApiVersion
import spk.plugin.api.PluginContext
import spk.plugin.api.PluginManifest

object : Plugin {
    override fun manifest(): PluginManifest =
        PluginManifest(
            "fixture.kotlin.dsl.raw-widget-denied",
            "1.0",
            PluginApiVersion.CURRENT,
            emptyList<String>()
        )

    override fun enable(context: PluginContext) {
        error(
            WidgetActionClientRequest::class.java.name
        )
    }
}
