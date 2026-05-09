/*
 *
 *  * Copyright (C) 2025 AKS-Labs (original author)
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.akslabs.circletosearch.utils

import android.content.Context
import android.content.SharedPreferences

class FriendlyMessageManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("friendly_msg_prefs", Context.MODE_PRIVATE)
    private val messages = listOf(
        "Ready when you are, boss. What’s the mission today? 🕵️‍♂️😂",
        "Say the word, chief. What are we hunting for now? 🔍😄",
        "Alright boss, what mystery are we solving this time? 🧐😂",
        "I’m here, captain! What’s the next target? 🎯🤣",
        "Reporting for duty, boss. What do we search? 💼😆"
    )

    fun getNextMessage(): String {
        val seenIndices = getSeenIndices()
        
        // Find available indices
        val allIndices = messages.indices.toSet()
        val availableIndices = allIndices.subtract(seenIndices).toList()

        if (availableIndices.isEmpty()) {
            // Reset if all seen
            clearSeenIndices()
            val newRandomIndex = messages.indices.random()
            markIndexSeen(newRandomIndex)
            return messages[newRandomIndex]
        }
        
        // Pick random from available
        val pickedIndex = availableIndices.random()
        markIndexSeen(pickedIndex)
        return messages[pickedIndex]
    }

    private fun getSeenIndices(): Set<Int> {
        val seenString = prefs.getString("seen_indices", "") ?: ""
        if (seenString.isEmpty()) return emptySet()
        return seenString.split(",").mapNotNull { it.toIntOrNull() }.toSet()
    }

    private fun markIndexSeen(index: Int) {
        val currentSeen = getSeenIndices().toMutableSet()
        currentSeen.add(index)
        prefs.edit().putString("seen_indices", currentSeen.joinToString(",")).apply()
    }

    private fun clearSeenIndices() {
        prefs.edit().remove("seen_indices").apply()
    }
}
