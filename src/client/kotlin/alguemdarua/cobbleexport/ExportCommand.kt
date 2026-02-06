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
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.io.File

object ExportCommand {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->

            val root = literal("cobble_export")

            // --- BRANCH 1: HELP ---
            root.then(literal("help").executes { ctx -> sendHelpMessage(ctx) })

            // --- BRANCH 2: PARTY ---
            val partyNode = literal("party")
                .executes { ctx -> exportParty(ctx, createNew = false) }
                .then(literal("new")
                    .executes { ctx -> exportParty(ctx, createNew = true) }
                )

            // --- BRANCH 3: BOX ---
            val boxNode = literal("box")
                .then(argument("boxNum", IntegerArgumentType.integer(1, 200))
                    .executes { ctx ->
                        val boxNum = IntegerArgumentType.getInteger(ctx, "boxNum")
                        exportBox(ctx, boxNum, createNew = false)
                    }
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
    //                                         HELP MENU
    // =================================================================================================

    private fun sendHelpMessage(ctx: CommandContext<FabricClientCommandSource>): Int {
        val source = ctx.source

        // Helper: Returns MutableText so we can chain styles later
        fun txt(content: String, color: Formatting, bold: Boolean = false): MutableText {
            val t = Text.literal(content).formatted(color)
            if (bold) t.formatted(Formatting.BOLD)
            return t
        }

        // Helper for clickable commands
        fun cmd(command: String, desc: String): MutableText {
            return Text.literal(" ➤ ")
                .formatted(Formatting.DARK_GRAY)
                .append(Text.literal(command).styled { style ->
                    style.withColor(Formatting.YELLOW)
                        .withClickEvent(ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                        .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to type")))
                })
                .append(Text.literal("\n     $desc").formatted(Formatting.GRAY))
        }

        val msg = Text.empty()

        // --- HEADER ---
        msg.append(Text.literal("\n▬▬▬▬▬▬▬▬▬▬ [ ").formatted(Formatting.DARK_GRAY))
        msg.append(txt("Cobblemon Export", Formatting.AQUA, true))
        msg.append(Text.literal(" ] ▬▬▬▬▬▬▬▬▬▬\n").formatted(Formatting.DARK_GRAY))

        // --- INTRO ---
        msg.append(txt("\nHello! This mod was made by ", Formatting.GRAY))
        msg.append(txt("AlguemDaRua", Formatting.GOLD, true))
        msg.append(txt(".\nIt's a small mod I created to access Pokemon info without having to check them one by one. I hope you enjoy it!\nPlease leave feedback so I can make it better. Thanks!\n\n", Formatting.GRAY).formatted(Formatting.ITALIC))

        // --- COMMANDS ---
        msg.append(txt("Available Commands:\n", Formatting.WHITE, true))

        // Party
        msg.append(cmd("/cobble_export party", "Overwrites the 'party_export.json' file."))
        msg.append(Text.literal("\n"))
        msg.append(cmd("/cobble_export party new", "Creates a NEW file (e.g. 'party_export_1.json')."))
        msg.append(Text.literal("\n"))

        // Box
        msg.append(cmd("/cobble_export box <num>", "Exports specific PC box (Overwrites file)."))
        msg.append(Text.literal("\n"))
        msg.append(cmd("/cobble_export box <num> new", "Exports PC box to a NEW file."))

        // --- FOOTER ---
        msg.append(txt("\n\nFiles are saved in: ", Formatting.WHITE))
        msg.append(txt(".minecraft/cobblemon_exports/", Formatting.GREEN))
        msg.append(Text.literal("\n▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬").formatted(Formatting.DARK_GRAY))

        source.sendFeedback(msg)
        return Command.SINGLE_SUCCESS
    }

    // =================================================================================================
    //                                         LOGIC HANDLERS
    // =================================================================================================

    private fun exportParty(ctx: CommandContext<FabricClientCommandSource>, createNew: Boolean): Int {
        val rawParty = CobblemonClient.storage.party

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
            val boxesList = getPcBoxesFromStorage()

            if (boxesList == null) {
                sendError(ctx, "Could not find PC Storage. Please open your PC block once to load data.")
                return 0
            }

            val index = boxNum - 1
            if (index < 0 || index >= boxesList.size) {
                sendError(ctx, "Box $boxNum does not exist (Max: ${boxesList.size}).")
                return 0
            }

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

    private fun getPcBoxesFromStorage(): List<*>? {
        val storage = CobblemonClient.storage
        val storageClass = storage.javaClass

        val storesField = storageClass.getDeclaredField("pcStores")
        storesField.isAccessible = true
        val pcStoresMap = storesField.get(storage) as Map<*, *>

        val player = MinecraftClient.getInstance().player ?: return null

        val myPC = pcStoresMap[player.uuid] ?: pcStoresMap.values.firstOrNull() ?: return null

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

        val file: File
        if (createNew) {
            var i = 1
            var candidate = File(exportDir, "${baseName}_$i.json")
            while (candidate.exists()) {
                i++
                candidate = File(exportDir, "${baseName}_$i.json")
            }
            file = candidate
        } else {
            file = File(exportDir, "$baseName.json")
        }

        try {
            file.writeText(gson.toJson(data))

            val clickableText = Text.literal("§e[OPEN FILE]")
                .styled { style ->
                    style.withColor(Formatting.YELLOW)
                        .withBold(true)
                        .withUnderline(true)
                        .withClickEvent(ClickEvent(ClickEvent.Action.OPEN_FILE, file.absolutePath))
                        .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to open ${file.name}")))
                }

            val message = Text.literal("§a[CobbleExport] Successfully saved to ")
                .append(Text.literal(file.name).formatted(Formatting.GRAY))
                .append(Text.literal(" "))
                .append(clickableText)

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