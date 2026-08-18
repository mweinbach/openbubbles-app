// Export raw, architecture-neutral p-code for selected instruction ranges.
// @category OpenBubbles.Recovery

import ghidra.app.cmd.disassemble.DisassembleCommand;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.address.AddressSet;
import ghidra.program.model.lang.Register;
import ghidra.program.model.listing.Instruction;
import ghidra.program.model.listing.InstructionIterator;
import ghidra.program.model.pcode.PcodeOp;
import ghidra.program.model.pcode.Varnode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ExportPcode extends GhidraScript {
    @Override
    public void run() throws Exception {
        String outputPath = null;
        StringBuilder output = new StringBuilder();

        for (String argument : getScriptArgs()) {
            if (argument.startsWith("--output=")) {
                outputPath = argument.substring("--output=".length());
                continue;
            }

            String[] parts = argument.split(":", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("range must be <address>:<length>");
            }
            Address start = toAddr(Long.decode(parts[0]));
            long length = Long.decode(parts[1]);
            Address end = start.add(length - 1);
            AddressSet body = new AddressSet(start, end);
            new DisassembleCommand(start, body, false).applyTo(currentProgram, monitor);

            output.append(String.format("R|%x|%x%n", start.getOffset(), end.getOffset()));
            InstructionIterator instructions = currentProgram.getListing().getInstructions(body, true);
            while (instructions.hasNext()) {
                Instruction instruction = instructions.next();
                Address next = instruction.getMaxAddress().next();
                output.append(String.format(
                    "I|%x|%x|%s%n",
                    instruction.getAddress().getOffset(),
                    next.getOffset(),
                    instruction.toString().replace('|', '/')
                ));
                for (PcodeOp op : instruction.getPcode()) {
                    output.append("P|").append(op.getMnemonic()).append('|');
                    appendVarnode(output, op.getOutput());
                    for (int index = 0; index < op.getNumInputs(); index++) {
                        output.append('|');
                        appendVarnode(output, op.getInput(index));
                    }
                    output.append('\n');
                }
                output.append("E\n");
            }
        }

        if (outputPath == null) {
            println(output.toString());
        } else {
            Files.writeString(Path.of(outputPath), output, StandardCharsets.UTF_8);
            println("Wrote p-code to " + outputPath);
        }
    }

    private void appendVarnode(StringBuilder output, Varnode varnode) {
        if (varnode == null) {
            output.append('-');
            return;
        }
        String space = varnode.getAddress().getAddressSpace().getName();
        String name = "";
        if (varnode.isRegister()) {
            Register register = currentProgram.getRegister(varnode.getAddress(), varnode.getSize());
            if (register != null) {
                name = register.getName();
            }
        }
        output.append(space)
            .append(':')
            .append(Long.toHexString(varnode.getOffset()))
            .append(':')
            .append(varnode.getSize())
            .append(':')
            .append(name);
    }
}
