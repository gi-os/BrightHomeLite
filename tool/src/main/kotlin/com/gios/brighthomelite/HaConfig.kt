package com.gios.brighthomelite

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Everything the tool needs to talk to one Home Assistant instance.
 *
 * [baseUrl] is http:// or https://. lighttool.toml declares the cleartext-http
 * capability, so the generated manifest allows plain http and a LAN address such as
 * http://192.168.1.20:8123 works. https (for example a Cloudflare tunnel) works too.
 */
@Serializable
data class HaConfig(
    val baseUrl: String,
    val token: String,
    val cfClientId: String? = null,
    val cfClientSecret: String? = null,
) {
    /** wss://host/api/websocket */
    val webSocketUrl: String
        get() = baseUrl.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") +
            "/api/websocket"

    /** Host only — safe to render. The token and the CF secret never are. */
    val displayHost: String
        get() = baseUrl.substringAfter("://").substringBefore('/')

    val usesAccess: Boolean
        get() = !cfClientId.isNullOrBlank() && !cfClientSecret.isNullOrBlank()
}

sealed interface PairingResult {
    data class Ok(val config: HaConfig) : PairingResult
    data class Invalid(val reason: String) : PairingResult
}

/**
 * Parses the pairing QR produced by docs/pair.html.
 *
 * Long-lived access tokens run to ~180 characters, which is not a thing anyone should
 * type on this keyboard, so the whole credential set travels as one scanned payload:
 *
 *     {"url":"https://ha.example.com","ha":"<token>","cfid":"...","cfsec":"..."}
 *
 * Long key names are accepted too so a hand-written payload works.
 */
object PairingPayload {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String): PairingResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return PairingResult.Invalid("Empty code.")
        if (!trimmed.startsWith("{")) {
            return PairingResult.Invalid("That is not a BrightHome setup code.")
        }

        val obj = runCatching { json.parseToJsonElement(trimmed) as? JsonObject }.getOrNull()
            ?: return PairingResult.Invalid("That code is not readable.")

        fun field(vararg names: String): String? {
            for (name in names) {
                val value = obj[name]?.jsonPrimitive?.contentOrNullSafe()?.trim()
                if (!value.isNullOrEmpty()) return value
            }
            return null
        }

        val url = field("url", "baseUrl", "base_url", "host")
            ?: return PairingResult.Invalid("The code has no Home Assistant address.")
        val token = field("ha", "token", "access_token", "accessToken")
            ?: return PairingResult.Invalid("The code has no access token.")

        return build(
            url = url,
            token = token,
            cfId = field("cfid", "cf_client_id", "cfClientId"),
            cfSecret = field("cfsec", "cf_client_secret", "cfClientSecret"),
        )
    }

    /** Shared by the QR path and the manual-entry fallback. */
    fun build(url: String, token: String, cfId: String?, cfSecret: String?): PairingResult {
        val normalized = normalizeUrl(url) ?: return PairingResult.Invalid(
            "That doesn't look like an address. Try http://192.168.1.20:8123 or https://ha.example.com.",
        )
        if (token.isBlank()) return PairingResult.Invalid("The access token is empty.")

        val id = cfId?.trim().orEmptyToNull()
        val secret = cfSecret?.trim().orEmptyToNull()
        if ((id == null) != (secret == null)) {
            return PairingResult.Invalid(
                "Cloudflare Access needs both the client ID and the client secret.",
            )
        }

        return PairingResult.Ok(
            HaConfig(
                baseUrl = normalized,
                token = token.trim(),
                cfClientId = id,
                cfClientSecret = secret,
            ),
        )
    }

    /**
     * Accepts http:// and https:// as-is. A bare host gets a scheme: http for something
     * that looks local (an IPv4 address, a .local name, or the default port 8123), https
     * for everything else.
     */
    fun normalizeUrl(raw: String): String? {
        var value = raw.trim().trimEnd('/')
        if (value.isEmpty()) return null
        val lower = value.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            value = (if (looksLocal(value)) "http://" else "https://") + value
        }
        val host = value.substringAfter("://").substringBefore('/')
        if (host.isEmpty() || host.any { it.isWhitespace() }) return null
        if (!host.contains('.') && !host.contains(':')) return null
        return value
    }

    private val ipv4 = Regex("""^\d{1,3}(\.\d{1,3}){3}(:\d+)?$""")

    private fun looksLocal(hostAndPath: String): Boolean {
        val host = hostAndPath.substringBefore('/').lowercase()
        return ipv4.matches(host) ||
            host.substringBefore(':').endsWith(".local") ||
            host.endsWith(":8123")
    }

    private fun String?.orEmptyToNull(): String? = if (isNullOrEmpty()) null else this
}

/** jsonPrimitive.contentOrNull without pulling in the whole JsonNull dance at each call site. */
private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content

internal object ConfigCodec {
    private val json = Json { ignoreUnknownKeys = true }
    fun encode(config: HaConfig): String = json.encodeToString(config)
    fun decode(raw: String): HaConfig? =
        runCatching { json.decodeFromString<HaConfig>(raw) }.getOrNull()
}

@Serializable
internal data class StoredFavorites(
    @SerialName("ids") val ids: List<String> = emptyList(),
)
