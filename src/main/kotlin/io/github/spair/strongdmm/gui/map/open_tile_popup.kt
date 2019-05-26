package io.github.spair.strongdmm.gui.map

import io.github.spair.strongdmm.gui.edit.ViewVariablesDialog
import io.github.spair.strongdmm.gui.instancelist.InstanceListView
import io.github.spair.strongdmm.gui.map.select.SelectOperation
import io.github.spair.strongdmm.gui.objtree.ObjectTreeView
import io.github.spair.strongdmm.logic.dme.*
import io.github.spair.strongdmm.logic.dmi.DmiProvider
import io.github.spair.strongdmm.logic.history.*
import io.github.spair.strongdmm.logic.map.*
import org.lwjgl.input.Mouse
import org.lwjgl.opengl.Display
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPopupMenu

fun MapPipeline.openTilePopup() {
    if (xMouseMap == OUT_OF_BOUNDS || yMouseMap == OUT_OF_BOUNDS) {
        return
    }

    MapView.createAndShowTilePopup(Mouse.getX(), Display.getHeight() - Mouse.getY()) { popup ->
        val tile = selectedMap!!.getTile(xMouseMap, yMouseMap) ?: return@createAndShowTilePopup

        with(popup) {
            addResetActions()
            addSeparator()
            addTileActions(selectedMap!!, tile)
            addSeparator()

            if (addOptionalSelectedInstanceActions(selectedMap!!, tile)) {
                addSeparator()
            }

            addTileItemsActions(selectedMap!!, tile)
        }
    }
}

private fun JPopupMenu.addResetActions() {
    add(JMenuItem("Undo").apply {
        isEnabled = History.hasUndoActions()
        addActionListener { History.undoAction() }
    })

    add(JMenuItem("Redo").apply {
        isEnabled = History.hasRedoActions()
        addActionListener { History.redoAction() }
    })
}

private fun JPopupMenu.addTileActions(map: Dmm, currentTile: Tile) {
    fun getPickedTiles() = SelectOperation.getPickedTiles()?.takeIf { it.isNotEmpty() }

    fun prepareReverseActions(pickedTiles: List<Tile>, fromCurrentTile: Boolean = true): CoordArea {
        val reverseActions = mutableListOf<Undoable>()
        val tilesArea = getAreaOfTiles(pickedTiles).let {
            if (fromCurrentTile) it.shiftToPoint(currentTile.x, currentTile.y) else it
        }

        for (x in tilesArea.x1..tilesArea.x2) {
            for (y in tilesArea.y1..tilesArea.y2) {
                map.getTile(x, y)?.let { reverseActions.add(TileReplaceAction(map, it)) }
            }
        }

        History.addUndoAction(MultipleAction(reverseActions))
        return tilesArea
    }

    add(JMenuItem("Cut").apply {
        addActionListener {
            val pickedTiles = getPickedTiles()

            if (pickedTiles != null) {
                prepareReverseActions(pickedTiles, false)
                TileOperation.cut(pickedTiles)
            } else {
                History.addUndoAction(TileReplaceAction(map, currentTile))
                TileOperation.cut(currentTile)
            }

            Frame.update(true)
        }
    })

    add(JMenuItem("Copy").apply {
        addActionListener {
            val pickedTiles = getPickedTiles()

            if (pickedTiles != null) {
                TileOperation.copy(pickedTiles)
            } else {
                TileOperation.copy(currentTile)
            }
        }
    })

    add(JMenuItem("Paste").apply {
        isEnabled = TileOperation.hasTileInBuffer()
        addActionListener {
            val pickedTiles = getPickedTiles()

            if (pickedTiles != null) {
                val tilesArea = prepareReverseActions(pickedTiles)
                SelectOperation.pickArea(tilesArea)
                TileOperation.paste(map, tilesArea.x1, tilesArea.y1)
            } else {
                History.addUndoAction(TileReplaceAction(map, currentTile))
                SelectOperation.pickArea(currentTile.x, currentTile.y)
                TileOperation.paste(map, currentTile.x, currentTile.y)
            }

            Frame.update(true)
        }
    })

    add(JMenuItem("Delete").apply {
        addActionListener {
            val pickedTiles = getPickedTiles()

            if (pickedTiles != null) {
                prepareReverseActions(pickedTiles, false)
                TileOperation.delete(pickedTiles)
            } else {
                History.addUndoAction(TileReplaceAction(map, currentTile))
                TileOperation.delete(currentTile)
            }

            Frame.update(true)
        }
    })

    if (SelectOperation.isPickType()) {
        add(JMenuItem("Deselect").apply {
            addActionListener {
                SelectOperation.depickArea()
            }
        })
    }
}

private fun JPopupMenu.addOptionalSelectedInstanceActions(map: Dmm, currentTile: Tile): Boolean {
    val selectedInstance = InstanceListView.selectedInstance ?: return false

    val selectedType = when {
        isType(selectedInstance.type, TYPE_TURF) -> TYPE_TURF
        isType(selectedInstance.type, TYPE_AREA) -> TYPE_AREA
        isType(selectedInstance.type, TYPE_MOB) -> TYPE_MOB
        else -> TYPE_OBJ
    }

    val selectedTypeName = selectedType.substring(1).capitalize()

    add(JMenuItem("Delete Topmost $selectedTypeName (Shift+Click)").apply {
        addActionListener {
            val topmostItem = currentTile.findTopmostTileItem(selectedType)

            if (topmostItem != null) {
                currentTile.deleteTileItem(topmostItem)
                History.addUndoAction(PlaceTileItemAction(map, topmostItem))
                Frame.update(true)
            }
        }
    })

    return true
}

private fun JPopupMenu.addTileItemsActions(map: Dmm, currentTile: Tile) {
    currentTile.getTileItems().forEach { tileItem ->
        val menu = JMenu("${tileItem.getVarText(VAR_NAME)} [${tileItem.type}]").apply {
            this@addTileItemsActions.add(this)
        }

        DmiProvider.getSpriteFromDmi(tileItem.icon, tileItem.iconState, tileItem.dir)?.let { spite ->
            menu.icon = spite.scaledIcon
        }

        menu.add(JMenuItem("Make Active Object (Ctrl+Shift+Click)").apply {
            addActionListener {
                ObjectTreeView.findAndSelectItemInstance(tileItem)
            }
        })

        menu.add(JMenuItem("Reset to Default").apply {
            addActionListener {
                History.addUndoAction(EditVarsAction(tileItem))
                tileItem.reset()
                Frame.update(true)
                InstanceListView.updateSelectedInstanceInfo()
            }
        })

        menu.add(JMenuItem("Delete")).apply {
            addActionListener {
                currentTile.deleteTileItem(tileItem)
                History.addUndoAction(PlaceTileItemAction(map, tileItem))
                Frame.update(true)
                InstanceListView.updateSelectedInstanceInfo()
            }
        }

        menu.add(JMenuItem("View Variables")).apply {
            addActionListener {
                if (ViewVariablesDialog(tileItem).open()) {
                    Frame.update(true)
                    InstanceListView.updateSelectedInstanceInfo()
                }
            }
        }
    }
}
