package com.unciv.logic.automation

import com.unciv.UnCivGame
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.UnitType
import com.unciv.ui.utils.getRandom
import com.unciv.ui.worldscreen.unit.UnitActions

class UnitAutomation{

    fun automateUnitMoves(unit: MapUnit) {

        if (unit.name == "Settler") {
            automateSettlerActions(unit)
            return
        }

        if (unit.name == "Worker") {
            WorkerAutomation().automateWorkerAction(unit)
            return
        }


        fun healUnit() {
            // If we're low on health then heal
            // todo: go to a more defensible place if there is one
            val tilesInDistance = unit.getDistanceToTiles().keys
            val unitTile = unit.getTile()

            // Go to friendly tile if within distance - better healing!
            val friendlyTile = tilesInDistance.firstOrNull { it.getOwner()?.civName == unit.owner && it.unit == null }
            if (unitTile.getOwner()?.civName != unit.owner && friendlyTile != null) {
                unit.moveToTile(friendlyTile)
                return
            }

            // Or at least get out of enemy territory yaknow
            val neutralTile = tilesInDistance.firstOrNull { it.getOwner() == null && it.unit == null }
            if (unitTile.getOwner()?.civName != unit.owner && unitTile.getOwner() != null && neutralTile != null) {
                unit.moveToTile(neutralTile)
                return
            }
        }

        if (unit.health < 50) {
            healUnit()
            return
        } // do nothing but heal

        // if there is an attackable unit in the vicinity, attack!
        val attackableTiles = unit.civInfo.getViewableTiles()
                .filter { it.unit != null && it.unit!!.owner != unit.civInfo.civName && !it.isCityCenter() }.toHashSet()
        val distanceToTiles = unit.getDistanceToTiles()
        val unitTileToAttack = distanceToTiles.keys.firstOrNull { attackableTiles.contains(it) }

        if (unitTileToAttack != null) {
            val unitToAttack = unitTileToAttack.unit!!
            if (unitToAttack.getBaseUnit().unitType == UnitType.Civilian) { // kill
                unitToAttack.civInfo.addNotification("Our " + unitToAttack.name + " was destroyed by an enemy " + unit.name + "!", unitTileToAttack.position)
                unitTileToAttack.unit = null
                unit.headTowards(unitTileToAttack.position)
                return
            }

            val damageToAttacker = Battle(unit.civInfo.gameInfo).calculateDamageToAttacker(MapUnitCombatant(unit), MapUnitCombatant(unitToAttack))

            if (damageToAttacker < unit.health) { // don't attack if we'll die from the attack
                if(unit.getBaseUnit().unitType == UnitType.Melee)
                    unit.headTowards(unitTileToAttack.position)
                Battle(unit.civInfo.gameInfo).attack(MapUnitCombatant(unit), MapUnitCombatant(unitToAttack))
                return
            }
        }

        if (unit.health < 80) {
            healUnit()
            return
        } // do nothing but heal until 80 health


        // else, if there is a reachable spot from which we can attack this turn
        // (say we're an archer and there's a unit 3 tiles away), go there and attack
        // todo

        // else, find the closest enemy unit that we know of within 5 spaces and advance towards it
        val closestUnit = unit.getTile().getTilesInDistance(5)
                .firstOrNull { attackableTiles.contains(it) }

        if (closestUnit != null) {
            unit.headTowards(closestUnit.position)
            return
        }

        if (unit.health < 100) {
            healUnit()
            return
        }

        // else, go to a random space
        unit.moveToTile(distanceToTiles.keys.filter { it.unit == null }.toList().getRandom())
    }


    fun rankTileAsCityCenter(tileInfo: TileInfo, nearbyTileRankings: Map<TileInfo, Float>): Float {
        val bestTilesFromOuterLayer = tileInfo.tileMap.getTilesAtDistance(tileInfo.position,2)
                .sortedByDescending { nearbyTileRankings[it] }.take(2)
        val top5Tiles = tileInfo.neighbors.union(bestTilesFromOuterLayer)
                .sortedByDescending { nearbyTileRankings[it] }
                .take(5)
        return top5Tiles.map { nearbyTileRankings[it]!! }.sum()
    }

    private fun automateSettlerActions(unit: MapUnit) {
        // find best city location within 5 tiles
        val tilesNearCities = unit.civInfo.gameInfo.civilizations.flatMap { it.cities }
                .flatMap { it.getCenterTile().getTilesInDistance(2) }

        // This is to improve performance - instead of ranking each tile in the area up to 19 times, do it once.
        val nearbyTileRankings = unit.getTile().getTilesInDistance(7)
                .associateBy ( {it},{ Automation().rankTile(it,unit.civInfo) })
        val bestCityLocation = unit.getTile().getTilesInDistance(5)
                .minus(tilesNearCities)
                .maxBy { rankTileAsCityCenter(it, nearbyTileRankings) }!!

        if (unit.getTile() == bestCityLocation)
            UnitActions().getUnitActions(unit, UnCivGame.Current.worldScreen!!).first { it.name == "Found city" }.action()
        else {
            unit.headTowards(bestCityLocation.position)
            if (unit.currentMovement > 0 && unit.getTile() == bestCityLocation)
                UnitActions().getUnitActions(unit, UnCivGame.Current.worldScreen!!).first { it.name == "Found city" }.action()
        }
    }

}