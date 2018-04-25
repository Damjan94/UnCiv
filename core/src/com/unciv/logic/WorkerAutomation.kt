package com.unciv.logic

import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.UnitMovementAlgorithms
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.TileImprovement

public class WorkerAutomation(){

    fun automateWorkerAction(unit: MapUnit) {
        var tile = unit.getTile()
        val tileToWork = findTileToWork(tile, unit.civInfo)
        if (tileToWork != tile) {
            tile = unit.headTowards(tileToWork.position)
            unit.doPreTurnAction(tile)
            return
        }
        if (tile.improvementInProgress == null) {
            val improvement = chooseImprovement(tile)
            if (tile.canBuildImprovement(improvement, unit.civInfo))
            // What if we're stuck on this tile but can't build there?
                tile.startWorkingOnImprovement(improvement, unit.civInfo)
        }
    }

    private fun findTileToWork(currentTile: TileInfo, civInfo: CivilizationInfo): TileInfo {
        val selectedTile = currentTile.tileMap.getTilesInDistance(currentTile.position, 4)
                .filter {
                    (it.unit == null || it == currentTile)
                            && it.improvement == null
                            && (it.getCity()==null || it.getCity()!!.civInfo == civInfo) // don't work tiles belonging to another civ
                            && it.canBuildImprovement(chooseImprovement(it), civInfo)
                            && UnitMovementAlgorithms(currentTile.tileMap) // the tile is actually reachable - more difficult than it seems!
                                .getShortestPath(currentTile.position, it.position, 2f, 2, civInfo).isNotEmpty()
                }
                .maxBy { getPriority(it, civInfo) }
        if (selectedTile != null && getPriority(selectedTile, civInfo) > getPriority(currentTile,civInfo)) return selectedTile
        else return currentTile
    }


    private fun getPriority(tileInfo: TileInfo, civInfo: CivilizationInfo): Int {
        var priority = 0
        if (tileInfo.isWorked()) priority += 3
        if (tileInfo.getOwner() == civInfo) priority += 2
        if (tileInfo.hasViewableResource(civInfo)) priority += 1
        else if (tileInfo.neighbors.any { it.getOwner() != null }) priority += 1
        return priority
    }

    private fun chooseImprovement(tile: TileInfo): TileImprovement {
        val improvementString = when {
            tile.improvementInProgress != null -> tile.improvementInProgress
            tile.terrainFeature == "Forest" -> "Lumber mill"
            tile.terrainFeature == "Jungle" -> "Trading post"
            tile.terrainFeature == "Marsh" -> "Remove Marsh"
            tile.resource != null -> tile.tileResource.improvement
            tile.baseTerrain == "Hill" -> "Mine"
            tile.baseTerrain == "Grassland" || tile.baseTerrain == "Desert" || tile.baseTerrain == "Plains" -> "Farm"
            tile.baseTerrain == "Tundra" -> "Trading post"
            else -> null
        }
        return GameBasics.TileImprovements[improvementString]!!
    }

}