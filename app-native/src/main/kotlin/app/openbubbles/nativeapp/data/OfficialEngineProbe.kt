package app.openbubbles.nativeapp.data

import com.sun.jna.NativeLibrary

internal data class OfficialEngineProbeResult(
    val loaded: Boolean,
    val flutterDispatcherExported: Boolean,
    val nativeStateExported: Boolean,
    val validationExports: Set<String>,
    val error: String? = null,
) {
    val directValidationAvailable: Boolean
        get() = REQUIRED_VALIDATION_EXPORTS.all(validationExports::contains)

    fun summary(): String = when {
        !loaded -> "official library unavailable: ${error ?: "not packaged"}"
        directValidationAvailable -> "official library loaded; direct validation ABI available"
        else -> "official library loaded; bridge exports present=" +
            "${flutterDispatcherExported || nativeStateExported}, direct validation ABI unavailable"
    }

    companion object {
        internal val REQUIRED_VALIDATION_EXPORTS = setOf(
            "nac_init",
            "nac_key_establishment",
            "nac_sign",
        )
    }
}

internal object OfficialEngineProbe {
    private const val LIBRARY_NAME = "openbubbles_official"
    private const val FLUTTER_DISPATCHER = "frb_pde_ffi_dispatcher_primary"
    private const val NATIVE_STATE_ENTRYPOINT =
        "uniffi_rust_lib_bluebubbles_fn_func_init_native"

    fun probe(): OfficialEngineProbeResult = runCatching {
        val library = NativeLibrary.getInstance(LIBRARY_NAME)
        evaluate { symbol -> runCatching { library.getFunction(symbol) }.isSuccess }
    }.getOrElse { error ->
        OfficialEngineProbeResult(
            loaded = false,
            flutterDispatcherExported = false,
            nativeStateExported = false,
            validationExports = emptySet(),
            error = error.message,
        )
    }

    internal fun evaluate(hasSymbol: (String) -> Boolean): OfficialEngineProbeResult {
        val validationExports = OfficialEngineProbeResult.REQUIRED_VALIDATION_EXPORTS
            .filterTo(linkedSetOf(), hasSymbol)
        return OfficialEngineProbeResult(
            loaded = true,
            flutterDispatcherExported = hasSymbol(FLUTTER_DISPATCHER),
            nativeStateExported = hasSymbol(NATIVE_STATE_ENTRYPOINT),
            validationExports = validationExports,
        )
    }
}
