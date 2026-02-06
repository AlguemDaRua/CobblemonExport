package alguemdarua.cobbleexport

import net.fabricmc.api.ClientModInitializer

object CobbleExportClient : ClientModInitializer {
	override fun onInitializeClient() {
		// This runs when the game launches
		ExportCommand.register()
		println("CobbleExport (Client) has initialized!")
	}
}