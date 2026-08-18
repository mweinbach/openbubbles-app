// Keep headless analysis focused on code flow for the stripped Rust image.
// @category OpenBubbles.Recovery

import ghidra.app.script.GhidraScript;

import java.util.Locale;
import java.util.Map;

public class ConfigureRecoveryAnalysis extends GhidraScript {
    @Override
    public void run() throws Exception {
        Map<String, String> options = getCurrentAnalysisOptionsAndValues(currentProgram);
        for (String name : options.keySet()) {
            String normalized = name.toLowerCase(Locale.ROOT);
            if (normalized.contains("exception") || normalized.contains("dwarf")) {
                setAnalysisOption(currentProgram, name, "false");
                println("Disabled analysis option: " + name);
            }
        }
    }
}
