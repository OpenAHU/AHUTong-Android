package com.ahu.ahutong.data.repository

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.test.Test
import kotlin.test.assertEquals

class RepositoryIndexTransferTest {
    @Test
    fun `index download reports content length and completes at the received byte count`() {
        val json = """{"updatedAt":7,"repositories":[],"files":[{"id":"one","repo":"cs","path":"a.md","name":"a.md"}],"dirs":[]}"""
        val body = json.toResponseBody("application/json".toMediaType())
        val updates = mutableListOf<Pair<Long, Long>>()

        val index = readStorageIndex(body, Gson()) { received, total ->
            updates += received to total
        }

        assertEquals(json.toByteArray().size.toLong(), updates.first().second)
        assertEquals(json.toByteArray().size.toLong(), updates.last().first)
        assertEquals(updates.last().first, updates.last().second)
        assertEquals("one", index.files.single().id)
    }
}
