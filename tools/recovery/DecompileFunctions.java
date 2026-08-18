// Decompile selected functions from the retained OpenBubbles compatibility image.
// @category OpenBubbles.Recovery

import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.cmd.disassemble.DisassembleCommand;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.address.AddressSet;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Instruction;
import ghidra.program.model.listing.InstructionIterator;
import ghidra.program.model.listing.Listing;
import ghidra.program.model.symbol.Reference;
import ghidra.program.model.symbol.SourceType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class DecompileFunctions extends GhidraScript {
    @Override
    public void run() throws Exception {
        DecompInterface decompiler = new DecompInterface();
        decompiler.toggleCCode(true);
        decompiler.toggleSyntaxTree(true);

        if (!decompiler.openProgram(currentProgram)) {
            throw new IllegalStateException("Unable to initialize the decompiler");
        }

        String outputPath = null;
        StringBuilder output = new StringBuilder();
        try {
            for (String argument : getScriptArgs()) {
                if (argument.startsWith("--output=")) {
                    outputPath = argument.substring("--output=".length());
                    continue;
                }
                String[] parts = argument.split(":", 2);
                Address address = toAddr(Long.decode(parts[0]));
                Function function;
                if (parts.length == 2) {
                    long length = Long.decode(parts[1]);
                    AddressSet body = new AddressSet(address, address.add(length - 1));
                    Listing listing = currentProgram.getListing();
                    function = listing.getFunctionAt(address);
                    if (function == null) {
                        Iterator<Function> iterator = currentProgram.getFunctionManager()
                            .getFunctionsOverlapping(body);
                        List<Address> entries = new ArrayList<>();
                        while (iterator.hasNext()) {
                            entries.add(iterator.next().getEntryPoint());
                        }
                        for (Address entry : entries) {
                            currentProgram.getFunctionManager().removeFunction(entry);
                        }
                        clearListing(address, address.add(length - 1));
                        Address instructionAddress = address;
                        Address end = address.add(length - 1);
                        while (instructionAddress.compareTo(end) <= 0) {
                            Instruction existing = listing.getInstructionAt(instructionAddress);
                            if (existing != null) {
                                instructionAddress = existing.getMaxAddress().next();
                                continue;
                            }
                            DisassembleCommand command = new DisassembleCommand(
                                instructionAddress,
                                body,
                                false
                            );
                            command.applyTo(currentProgram, monitor);
                            Instruction decoded = listing.getInstructionAt(instructionAddress);
                            instructionAddress = decoded == null
                                ? instructionAddress.next()
                                : decoded.getMaxAddress().next();
                        }
                        function = listing.createFunction(
                            "recovered_" + address,
                            address,
                            body,
                            SourceType.USER_DEFINED
                        );
                    }

                    int returningCalls = 0;
                    InstructionIterator instructions = listing.getInstructions(body, true);
                    while (instructions.hasNext()) {
                        Instruction instruction = instructions.next();
                        for (Reference reference : instruction.getReferencesFrom()) {
                            if (!reference.getReferenceType().isCall()) {
                                continue;
                            }
                            Address target = reference.getToAddress();
                            if (!currentProgram.getMemory().contains(target) || body.contains(target)) {
                                continue;
                            }
                            Function callee = listing.getFunctionAt(target);
                            if (callee == null) {
                                disassemble(target);
                                callee = createFunction(target, null);
                            }
                            if (callee != null) {
                                callee.setNoReturn(false);
                                returningCalls++;
                            }
                        }
                    }
                    println("Marked " + returningCalls + " direct calls as returning");
                } else {
                    function = getFunctionAt(address);
                    if (function == null) {
                        disassemble(address);
                        function = createFunction(address, null);
                    }
                }
                if (function == null) {
                    printerr("No function at " + argument);
                    continue;
                }

                String heading = "===== " + argument + " " + function.getName() + " =====";
                println(heading);
                Instruction entryInstruction = currentProgram.getListing().getInstructionAt(address);
                byte[] entryBytes = new byte[16];
                currentProgram.getMemory().getBytes(address, entryBytes);
                StringBuilder entryHex = new StringBuilder();
                for (byte value : entryBytes) {
                    entryHex.append(String.format("%02x", value & 0xff));
                }
                println(
                    "Body bytes=" + function.getBody().getNumAddresses() +
                    " thunk=" + function.isThunk() +
                    " entry=" + (entryInstruction == null ? "<none>" : entryInstruction.toString()) +
                    " bytes=" + entryHex
                );
                output.append(heading).append('\n');
                DecompileResults result = decompiler.decompileFunction(function, 180, monitor);
                if (!result.decompileCompleted()) {
                    printerr("Decompiler error at " + argument + ": " + result.getErrorMessage());
                    continue;
                }
                output.append(result.getDecompiledFunction().getC()).append('\n');
            }
            if (outputPath != null) {
                Files.writeString(Path.of(outputPath), output, StandardCharsets.UTF_8);
                println("Wrote decompilation to " + outputPath);
            } else {
                println(output.toString());
            }
        } finally {
            decompiler.dispose();
        }
    }
}
