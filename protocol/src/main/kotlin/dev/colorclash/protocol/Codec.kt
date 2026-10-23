package dev.colorclash.protocol

import kotlinx.serialization.json.Json

object Codec {
    val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
    }

    fun encode(message: ClientMessage): String = json.encodeToString(ClientMessage.serializer(), message)
    fun encode(message: ServerMessage): String = json.encodeToString(ServerMessage.serializer(), message)
    fun decodeClient(text: String): ClientMessage = json.decodeFromString(ClientMessage.serializer(), text)
    fun decodeServer(text: String): ServerMessage = json.decodeFromString(ServerMessage.serializer(), text)
}
