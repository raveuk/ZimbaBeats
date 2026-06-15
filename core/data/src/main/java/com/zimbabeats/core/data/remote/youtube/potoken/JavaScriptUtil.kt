package com.zimbabeats.core.data.remote.youtube.potoken

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

private val json = Json { ignoreUnknownKeys = true }

/**
 * Parses the raw challenge data obtained from the Create endpoint and returns a JSON object that
 * can be embedded in a JavaScript snippet and consumed by `runBotGuard()` in po_token.html.
 */
fun parseChallengeData(rawChallengeData: String): String {
    val scrambled = json.parseToJsonElement(rawChallengeData).jsonArray

    val challengeData = if (scrambled.size > 1 && scrambled[1].let { it is JsonPrimitive && it.isString }) {
        val descrambled = descramble(scrambled[1].jsonPrimitive.content)
        json.parseToJsonElement(descrambled).jsonArray
    } else {
        scrambled[0].jsonArray
    }

    val messageId = challengeData.getOrNull(0)?.jsonPrimitive?.content
    val interpreterHash = challengeData.getOrNull(3)?.jsonPrimitive?.content
    val program = challengeData.getOrNull(4)?.jsonPrimitive?.content
    val globalName = challengeData.getOrNull(5)?.jsonPrimitive?.content
    val clientExperimentsStateBlob = challengeData.getOrNull(7)?.jsonPrimitive?.content

    val safeScriptValue = findFirstString(challengeData.getOrNull(1))
    val trustedResourceUrlValue = findFirstString(challengeData.getOrNull(2))

    val result = buildJsonObject {
        putNullableString("messageId", messageId)
        put(
            "interpreterJavascript",
            buildJsonObject {
                putNullableString("privateDoNotAccessOrElseSafeScriptWrappedValue", safeScriptValue)
                putNullableString("privateDoNotAccessOrElseTrustedResourceUrlWrappedValue", trustedResourceUrlValue)
            },
        )
        putNullableString("interpreterHash", interpreterHash)
        putNullableString("program", program)
        putNullableString("globalName", globalName)
        putNullableString("clientExperimentsStateBlob", clientExperimentsStateBlob)
    }
    return json.encodeToString(JsonObject.serializer(), result)
}

private fun JsonObjectBuilder.putNullableString(key: String, value: String?) {
    put(key, if (value == null) JsonNull else JsonPrimitive(value))
}

private fun findFirstString(element: JsonElement?): String? {
    val array = element as? JsonArray ?: return null
    return array.firstOrNull { it is JsonPrimitive && it.isString }?.jsonPrimitive?.content
}

/**
 * Parses the raw integrity token data obtained from the GenerateIT endpoint to a JavaScript
 * `Uint8Array` literal that can be embedded directly in JavaScript code, and a [Long] representing
 * the duration of this token in seconds.
 */
fun parseIntegrityTokenData(rawIntegrityTokenData: String): Pair<String, Long> {
    val integrityTokenData = json.parseToJsonElement(rawIntegrityTokenData).jsonArray
    val token = base64ToU8(integrityTokenData[0].jsonPrimitive.content)
    val expiration = integrityTokenData[1].jsonPrimitive.content.toLong()
    return token to expiration
}

/**
 * Converts a string (usually the identifier used as input to `obtainPoToken`) to a JavaScript
 * `Uint8Array` literal that can be embedded directly in JavaScript code.
 */
fun stringToU8(identifier: String): String = newUint8Array(identifier.toByteArray(Charsets.UTF_8))

/**
 * Takes a poToken encoded as a sequence of bytes represented as integers separated by commas
 * (e.g. "97,98,99" would be "abc"), which is the output of `Uint8Array::toString()` in JavaScript,
 * and converts it to the specific base64 representation used for poTokens.
 */
fun u8ToBase64(poToken: String): String {
    val bytes = poToken.split(",")
        .map { it.trim().toInt().toByte() }
        .toByteArray()
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
        .replace("+", "-")
        .replace("/", "_")
}

/** Takes the scrambled challenge, decodes it from base64, adds 97 to each byte. */
private fun descramble(scrambledChallenge: String): String {
    val bytes = base64ToByteArray(scrambledChallenge)
    val descrambled = ByteArray(bytes.size) { (bytes[it] + 97).toByte() }
    return String(descrambled, Charsets.UTF_8)
}

/**
 * Decodes a base64 string encoded in the specific base64 representation used by YouTube, and
 * returns a JavaScript `Uint8Array` literal that can be embedded directly in JavaScript code.
 */
private fun base64ToU8(base64: String): String = newUint8Array(base64ToByteArray(base64))

private fun newUint8Array(contents: ByteArray): String =
    "new Uint8Array([" + contents.joinToString(separator = ",") { (it.toInt() and 0xFF).toString() } + "])"

/** Decodes a base64 string encoded in the specific base64 representation used by YouTube. */
private fun base64ToByteArray(base64: String): ByteArray {
    val base64Mod = base64
        .replace('-', '+')
        .replace('_', '/')
        .replace('.', '=')
    return try {
        Base64.decode(base64Mod, Base64.DEFAULT)
    } catch (e: IllegalArgumentException) {
        throw PoTokenException("Cannot base64 decode: ${e.message}")
    }
}
