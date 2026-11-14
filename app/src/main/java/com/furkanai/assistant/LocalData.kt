package com.furkanai.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object LocalData {
    private const val PREFS = "furkan_ai_local_data"
    private const val MESSAGES = "messages"
    private const val MEMORIES = "memories"
    private const val REMINDERS = "reminders"
    private const val NOTES = "notes"
    private const val PROFILE_NAME = "profile_name"
    private const val PROFILE_PROMPT = "profile_prompt"

    data class Reminder(val id: Int, val title: String, val atMillis: Long)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadMessages(context: Context): List<ChatMessage> {
        val raw = prefs(context).getString(MESSAGES, "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(ChatMessage(item.optString("role"), item.optString("text")))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveMessages(context: Context, messages: List<ChatMessage>) {
        val array = JSONArray()
        messages.takeLast(120).forEach {
            array.put(JSONObject().put("role", it.role).put("text", it.text))
        }
        prefs(context).edit().putString(MESSAGES, array.toString()).apply()
    }

    fun clearMessages(context: Context) {
        prefs(context).edit().remove(MESSAGES).apply()
    }

    fun memories(context: Context): List<String> {
        return try {
            val array = JSONArray(prefs(context).getString(MEMORIES, "[]") ?: "[]")
            buildList {
                for (i in 0 until array.length()) {
                    val value = array.optString(i).trim()
                    if (value.isNotBlank()) add(value)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addMemory(context: Context, value: String) {
        val clean = value.trim()
        if (clean.isBlank()) return
        val values = (memories(context) + clean).distinct().takeLast(100)
        val array = JSONArray()
        values.forEach(array::put)
        prefs(context).edit().putString(MEMORIES, array.toString()).apply()
    }

    fun deleteMemory(context: Context, value: String) {
        val array = JSONArray()
        memories(context).filterNot { it == value }.forEach(array::put)
        prefs(context).edit().putString(MEMORIES, array.toString()).apply()
    }

    fun profileName(context: Context): String =
        prefs(context).getString(PROFILE_NAME, "Jarvis") ?: "Jarvis"

    fun profilePrompt(context: Context): String =
        prefs(context).getString(PROFILE_PROMPT, "") ?: ""

    fun saveProfile(context: Context, name: String, prompt: String) {
        prefs(context).edit()
            .putString(PROFILE_NAME, name.trim().ifBlank { "Jarvis" })
            .putString(PROFILE_PROMPT, prompt.trim())
            .apply()
    }


    data class Note(val id: Int, val title: String, val body: String)

    fun notes(context: Context): List<Note> {
        return try {
            val array = JSONArray(prefs(context).getString(NOTES, "[]") ?: "[]")
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(Note(item.optInt("id"), item.optString("title"), item.optString("body")))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addNote(context: Context, title: String, body: String) {
        val values = (notes(context) + Note((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), title.trim(), body.trim())).takeLast(100)
        val array = JSONArray()
        values.forEach { note ->
            array.put(JSONObject().put("id", note.id).put("title", note.title).put("body", note.body))
        }
        prefs(context).edit().putString(NOTES, array.toString()).apply()
    }

    fun deleteNote(context: Context, id: Int) {
        val array = JSONArray()
        notes(context).filterNot { it.id == id }.forEach { note ->
            array.put(JSONObject().put("id", note.id).put("title", note.title).put("body", note.body))
        }
        prefs(context).edit().putString(NOTES, array.toString()).apply()
    }

    fun reminders(context: Context): List<Reminder> {
        return try {
            val array = JSONArray(prefs(context).getString(REMINDERS, "[]") ?: "[]")
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        Reminder(
                            item.optInt("id"),
                            item.optString("title"),
                            item.optLong("at")
                        )
                    )
                }
            }.filter { it.atMillis > System.currentTimeMillis() }.sortedBy { it.atMillis }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addReminder(context: Context, reminder: Reminder) {
        val values = reminders(context).toMutableList()
        values.removeAll { it.id == reminder.id }
        values.add(reminder)
        val array = JSONArray()
        values.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("at", it.atMillis)
            )
        }
        prefs(context).edit().putString(REMINDERS, array.toString()).apply()
    }

    fun deleteReminder(context: Context, id: Int) {
        val array = JSONArray()
        reminders(context).filterNot { it.id == id }.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("at", it.atMillis)
            )
        }
        prefs(context).edit().putString(REMINDERS, array.toString()).apply()
        ReminderScheduler.cancel(context, id)
    }
}
