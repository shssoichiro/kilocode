package ai.kilocode.jetbrains

/** Represents the state of the Kilo CLI server connection. */
sealed class KiloConnection {
    data class Ready(val port: Int, val password: String) : KiloConnection()
    data class Error(val message: String, val details: String? = null) : KiloConnection()
}
