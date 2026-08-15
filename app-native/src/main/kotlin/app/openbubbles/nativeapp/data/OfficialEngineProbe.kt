package app.openbubbles.nativeapp.data

import com.sun.jna.NativeLibrary

internal data class OfficialEngineProbeResult(
    val loaded: Boolean,
    val flutterDispatcherExported: Boolean,
    val nativeStateExported: Boolean,
    val compatibilityAnchorExported: Boolean,
    val error: String? = null,
) {
    val onDeviceValidationAvailable: Boolean
        get() = loaded && compatibilityAnchorExported

    fun summary(): String = when {
        !loaded -> "official library unavailable: ${error ?: "not packaged"}"
        onDeviceValidationAvailable ->
            "official library loaded; version-pinned on-device validation backend available"
        else -> "official library loaded; bridge exports present=" +
            "${flutterDispatcherExported || nativeStateExported}, compatibility anchor unavailable"
    }
}

internal object OfficialEngineProbe {
    private const val LIBRARY_NAME = "openbubbles_official"
    private const val FLUTTER_DISPATCHER = "frb_pde_ffi_dispatcher_primary"
    private const val NATIVE_STATE_ENTRYPOINT =
        "uniffi_rust_lib_bluebubbles_fn_func_init_native"
    private const val COMPATIBILITY_ANCHOR =
        "ffi_rust_lib_bluebubbles_uniffi_contract_version"

    fun probe(): OfficialEngineProbeResult = runCatching {
        val library = NativeLibrary.getInstance(LIBRARY_NAME)
        evaluate { symbol -> runCatching { library.getFunction(symbol) }.isSuccess }
    }.getOrElse { error ->
        OfficialEngineProbeResult(
            loaded = false,
            flutterDispatcherExported = false,
            nativeStateExported = false,
            compatibilityAnchorExported = false,
            error = error.message,
        )
    }

    internal fun evaluate(hasSymbol: (String) -> Boolean): OfficialEngineProbeResult {
        return OfficialEngineProbeResult(
            loaded = true,
            flutterDispatcherExported = hasSymbol(FLUTTER_DISPATCHER),
            nativeStateExported = hasSymbol(NATIVE_STATE_ENTRYPOINT),
            compatibilityAnchorExported = hasSymbol(COMPATIBILITY_ANCHOR),
        )
    }
}
