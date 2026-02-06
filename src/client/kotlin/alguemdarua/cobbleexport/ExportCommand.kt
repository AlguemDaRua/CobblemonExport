package alguemdarua.cobbleexport

import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.pokemon.Pokemon
import com.google.gson.GsonBuilder
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.MinecraftClient
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.io.File

object ExportCommand {

    // Pretty printing makes the JSON human-readable
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->

            val root = literal("cobble_export")

            // --- BRANCH 1: PARTY ---
            val partyNode = literal("party")
                // Case A: /cobble_export party (Overwrites)
                .executes { ctx -> exportParty(ctx, createNew = false) }
                // Case B: /cobble_export party new (Creates new file)
                .then(literal("new")
                    .executes { ctx -> exportParty(ctx, createNew = true) }
                )

            // --- BRANCH 2: BOX ---
            val boxNode = literal("box")
                .then(argument("boxNum", IntegerArgumentType.integer(1, 200)) // Support large servers
                    // Case A: /cobble_export box <num> (Overwrites)
                    .executes { ctx ->
                        val boxNum = IntegerArgumentType.getInteger(ctx, "boxNum")
                        exportBox(ctx, boxNum, createNew = false)
                    }
                    // Case B: /cobble_export box <num> new (Creates new file)
                    .then(literal("new")
                        .executes { ctx ->
                            val boxNum = IntegerArgumentType.getInteger(ctx, "boxNum")
                            exportBox(ctx, boxNum, createNew = true)
                        }
                    )
                )

            root.then(partyNode)
            root.then(boxNode)
            dispatcher.register(root)
        }
    }

    // =================================================================================================
    //                                         LOGIC HANDLERS
    // =================================================================================================

    private fun exportParty(ctx: CommandContext<FabricClientCommandSource>, createNew: Boolean): Int {
        val rawParty = CobblemonClient.storage.party

        // Cast to Iterable to avoid Kotlin ambiguity errors with Cobblemon's custom classes
        val safeIterable = rawParty as Iterable<*>
        val exportData = extractPokemonFromList(safeIterable)

        if (exportData.isEmpty()) {
            sendError(ctx, "Your party is empty!")
            return 0
        }

        saveJson(ctx.source, "party_export", exportData, createNew)
        return Command.SINGLE_SUCCESS
    }

    private fun exportBox(ctx: CommandContext<FabricClientCommandSource>, boxNum: Int, createNew: Boolean): Int {
        try {
            // 1. Retrieve the PC Boxes using our helper (handles the Reflection logic)
            val boxesList = getPcBoxesFromStorage()

            if (boxesList == null) {
                sendError(ctx, "Could not find PC Storage. Please open your PC block once to load data.")
                return 0
            }

            // 2. Validate Box Number
            val index = boxNum - 1
            if (index < 0 || index >= boxesList.size) {
                sendError(ctx, "Box $boxNum does not exist (Max: ${boxesList.size}).")
                return 0
            }

            // 3. Extract Pokemon
            val targetBox = boxesList[index] as Iterable<*>
            val exportData = extractPokemonFromList(targetBox)

            if (exportData.isEmpty()) {
                sendError(ctx, "Box $boxNum is empty!")
                return 0
            }

            saveJson(ctx.source, "box_${boxNum}_export", exportData, createNew)
            return Command.SINGLE_SUCCESS

        } catch (e: Exception) {
            e.printStackTrace()
            sendError(ctx, "Critical Error: ${e.message}")
            return 0
        }
    }

    /**
     * Helper to loop through a generic list and map any Pokemon found.
     */
    private fun extractPokemonFromList(list: Iterable<*>): List<Map<String, Any?>> {
        val data = mutableListOf<Map<String, Any?>>()
        for (obj in list) {
            if (obj != null && obj is Pokemon) {
                data.add(PokemonMapper.toMap(obj))
            }
        }
        return data
    }

    // =================================================================================================
    //                                      REFLECTION HELPERS
    // =================================================================================================

    /**
     * Uses Reflection to safely grab the Box List from the internal Client Storage.
     * Handles the 'pcStores' Map logic found in Cobblemon 1.7.
     */
    private fun getPcBoxesFromStorage(): List<*>? {
        val storage = CobblemonClient.storage
        val storageClass = storage.javaClass

        // 1. Access the 'pcStores' Map (UUID -> ClientPC)
        val storesField = storageClass.getDeclaredField("pcStores")
        storesField.isAccessible = true
        val pcStoresMap = storesField.get(storage) as Map<*, *>

        // 2. Find the correct PC for this player
        val player = MinecraftClient.getInstance().player ?: return null

        // Try getting by UUID, otherwise fallback to the first available PC (Robustness)
        val myPC = pcStoresMap[player.uuid] ?: pcStoresMap.values.firstOrNull() ?: return null

        // 3. Find the 'boxes' list inside the ClientPC object
        // We scan fields to find the list, handling potential name changes/obfuscation
        val pcFields = myPC.javaClass.declaredFields

        val boxesField = pcFields.find { it.name == "boxes" }
            ?: pcFields.find { it.name == "allBoxes" }
            ?: pcFields.find { it.type == List::class.java || it.type == java.util.ArrayList::class.java }

        if (boxesField == null) return null

        boxesField.isAccessible = true
        return boxesField.get(myPC) as List<*>
    }

    // =================================================================================================
    //                                      FILE & CHAT UTILS
    // =================================================================================================

    private fun saveJson(source: FabricClientCommandSource, baseName: String, data: Any, createNew: Boolean) {
        val runDir = MinecraftClient.getInstance().runDirectory
        val exportDir = File(runDir, "cobblemon_exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        // Determine filename
        val file: File
        if (createNew) {
            // Incremental: Find the next available number (box_1_export_1.json, box_1_export_2.json)
            var i = 1
            var candidate = File(exportDir, "${baseName}_$i.json")
            while (candidate.exists()) {
                i++
                candidate = File(exportDir, "${baseName}_$i.json")
            }
            file = candidate
        } else {
            // Overwrite: Just use the base name
            file = File(exportDir, "$baseName.json")
        }

        try {
            file.writeText(gson.toJson(data))

            // Create a clickable, hoverable success message
            val clickableText = Text.literal("§e[OPEN FILE]")
                .styled { style ->
                    style.withColor(Formatting.YELLOW)
                        .withBold(true)
                        .withUnderline(true)
                        .withClickEvent(ClickEvent(ClickEvent.Action.OPEN_FILE, file.absolutePath))
                        .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to open ${file.name}")))
                }

            val message = Text.literal("§a[CobbleExport] Successfully saved to ")
                .append(Text.literal(file.name).formatted(Formatting.GRAY)) // Show filename
                .append(Text.literal(" "))
                .append(clickableText) // Add the button

            source.sendFeedback(message)

        } catch (e: Exception) {
            sendError(source, "Failed to write file: ${e.message}")
        }
    }

    private fun sendError(ctx: CommandContext<FabricClientCommandSource>, msg: String) {
        ctx.source.sendFeedback(Text.literal("§c[CobbleExport] $msg"))
    }

    private fun sendError(source: FabricClientCommandSource, msg: String) {
        source.sendFeedback(Text.literal("§c[CobbleExport] $msg"))
    }
}