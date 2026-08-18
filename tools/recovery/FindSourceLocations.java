// Find Rust panic-location records for a retained source-path string.
// @category OpenBubbles.Recovery

import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Instruction;
import ghidra.program.model.mem.Memory;
import ghidra.program.model.symbol.Reference;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class FindSourceLocations extends GhidraScript {
    @Override
    public void run() throws Exception {
        if (getScriptArgs().length != 1) {
            throw new IllegalArgumentException("usage: FindSourceLocations.java <path-string-address>");
        }

        Address pathAddress = toAddr(Long.decode(getScriptArgs()[0]));
        Memory memory = currentProgram.getMemory();
        byte[] pointer = ByteBuffer.allocate(8)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putLong(pathAddress.getOffset())
            .array();

        Address cursor = memory.getMinAddress();
        while (cursor != null && cursor.compareTo(memory.getMaxAddress()) <= 0) {
            Address occurrence = memory.findBytes(
                cursor,
                memory.getMaxAddress(),
                pointer,
                null,
                true,
                monitor
            );
            if (occurrence == null) {
                break;
            }

            long pathLength = memory.getLong(occurrence.add(8));
            int line = memory.getInt(occurrence.add(16));
            int column = memory.getInt(occurrence.add(20));
            println(String.format(
                "location=%s path_len=%d line=%d column=%d",
                occurrence,
                pathLength,
                line,
                column
            ));

            for (Reference reference : getReferencesTo(occurrence)) {
                Address from = reference.getFromAddress();
                Instruction instruction = getInstructionAt(from);
                Function function = getFunctionContaining(from);
                println(String.format(
                    "  ref=%s type=%s function=%s instruction=%s",
                    from,
                    reference.getReferenceType(),
                    function == null ? "<none>" : function.getName(),
                    instruction == null ? "<data>" : instruction.toString()
                ));
            }
            cursor = occurrence.next();
        }
    }
}
