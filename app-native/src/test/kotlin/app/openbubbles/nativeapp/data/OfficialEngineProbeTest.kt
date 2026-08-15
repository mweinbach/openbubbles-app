package app.openbubbles.nativeapp.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfficialEngineProbeTest {
    @Test
    fun `bridge-only library is not treated as direct validation engine`() {
        val result = OfficialEngineProbe.evaluate { symbol ->
            symbol == "frb_pde_ffi_dispatcher_primary" ||
                symbol == "uniffi_rust_lib_bluebubbles_fn_func_init_native"
        }

        assertTrue(result.loaded)
        assertTrue(result.flutterDispatcherExported)
        assertTrue(result.nativeStateExported)
        assertFalse(result.directValidationAvailable)
    }

    @Test
    fun `all required nac exports make direct validation available`() {
        val result = OfficialEngineProbe.evaluate { symbol ->
            symbol in OfficialEngineProbeResult.REQUIRED_VALIDATION_EXPORTS
        }

        assertTrue(result.directValidationAvailable)
    }
}
