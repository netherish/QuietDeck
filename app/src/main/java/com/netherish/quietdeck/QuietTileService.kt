package com.netherish.quietdeck

import android.app.NotificationManager
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class QuietTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        val manager = getSystemService(NotificationManager::class.java)
        if (manager?.isNotificationPolicyAccessGranted == true) {
            val next = if (manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                NotificationManager.INTERRUPTION_FILTER_PRIORITY
            } else {
                NotificationManager.INTERRUPTION_FILTER_ALL
            }
            try {
                manager.setInterruptionFilter(next)
            } catch (_: Exception) {
                // Tile stays in sync with the real system state on the next refresh.
            }
            updateTile()
        } else {
            val intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(intent)
            } catch (_: Exception) {
                // Tile tap is a best-effort shortcut.
            }
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val manager = getSystemService(NotificationManager::class.java)
        val active = manager?.isNotificationPolicyAccessGranted == true &&
            manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL

        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (active) "Quiet on" else "QuietDeck"
        tile.contentDescription = if (active) {
            "Do Not Disturb is active. Tap to turn it off."
        } else {
            "Open QuietDeck or turn on Do Not Disturb."
        }
        tile.updateTile()
    }
}
