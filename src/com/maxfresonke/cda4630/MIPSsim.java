package com.maxfresonke.cda4630;

import javax.xml.transform.Source;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class MIPSsim {

    private static final String FILENAME_INPUT_INSTRUCTIONS = "instructions.txt";
    private static final String FILENAME_INPUT_REGISTER = "registers.txt";
    private static final String FILENAME_INPUT_DATA_MEMORY = "datamemory.txt";
    private static final int NUM_REGS = 8;

    public static void main(String[] args) throws IOException {
        RegisterFile rgf = new RegisterFile(FILENAME_INPUT_REGISTER);
        InstructionMemory inm = new InstructionMemory(rgf, FILENAME_INPUT_INSTRUCTIONS);
        InstructionBuffer inb = new InstructionBuffer(inm, rgf);
        LoadInstructionBuffer lib = new LoadInstructionBuffer(inb.getIssue2DataRetriever());
        DataMemory dam = new DataMemory(NUM_REGS, FILENAME_INPUT_DATA_MEMORY);
        AddressBuffer adb = new AddressBuffer(lib, dam);

        Steppable[] steps = {
            inm, inb, lib, rgf, adb
        };

        OutputLiner[] outputs = {
            inm, inb, lib, adb, rgf, dam
        };

        StringBuilder output = new StringBuilder();

        boolean stepsLeft = false;
        int count = 0;
        do {
            // output current iteration
            output.append("STEP " + count + ":" + "\n");
            for (OutputLiner s : outputs) {
                output.append(s.getOutputLine());
                output.append("\n");
            }
            // fill all buffers
            for (Steppable s : steps) {
                s.fillBuffer();
            }

            // step current iteration
            stepsLeft = false;
            for (Steppable s : steps) {
                if (s.step()) {
                    stepsLeft = true;
                }
            }
            if (stepsLeft) {
                output.append("\n\n");
            }
            ++count;
            System.out.println(output.toString());
            output = new StringBuilder();
        } while (stepsLeft);


    }
}

/* Primary Interfaces */
interface OutputLiner {
    String getOutputLine();
}
interface Steppable extends OutputLiner {
    void fillBuffer();
    boolean step(); // Returns true if was able to step
}
interface DataRetriever<O> {
    O getData();
}

/* Pure Helper Types */
enum Register {
    R0(0), R1(1), R2(2), R3(3), R4(4), R5(5), R6(6), R7(7);
    private int index;
    Register(int index) {
        this.index = index;
    }
    static Register fromString(String rStr) {
        for (Register r : values()) {
            if (r.toString().equals(rStr)) {
                return r;
            }
        }
        throw new IllegalArgumentException(rStr);
    }
    public int getIndex() { return index; }
}
class Instruction {

    private String opcode;
    private Register dest;
    private Register source1;
    private Register source2;

    public Instruction(String opcode, Register dest, Register source1, Register source2) {
        this.opcode = opcode;
        this.dest = dest;
        this.source1 = source1;
        this.source2 = source2;
    }

    protected Instruction(Instruction i) {
        this.opcode = i.opcode;
        this.dest = i.dest;
        this.source1 = i.source1;
        this.source2 = i.source2;
    }

    public String getOpcode() {
        return opcode;
    }

    public Register getDest() {
        return dest;
    }

    public Register getSource1() {
        return source1;
    }

    public Register getSource2() {
        return source2;
    }

    @Override
    public String toString() {
        return "<" + getOpcode() + "," + getDest() + "," + getSource1() + "," +
                getSource2() + ">";
    }
}
class ValueInstruction extends Instruction {

    SourceRegisterDataSet sourceRegisterDataSet;

    protected ValueInstruction(Instruction i, SourceRegisterDataSet sourceRegisterDataSet) {
        super(i);
        this.sourceRegisterDataSet = sourceRegisterDataSet;
    }

    public byte getRegister1Data() {
        return sourceRegisterDataSet.getSourceReg1Data();
    }

    public byte getRegister2Data() {
        return sourceRegisterDataSet.getSourceReg2Data();
    }

    @Override
    public String toString() {
        return "<" + getOpcode() + "," + getDest().toString() + "," +
        sourceRegisterDataSet.getSourceReg1Data() + "," + sourceRegisterDataSet.getSourceReg2Data() + ">";
    }
}
class AddressDecodedInstruction {
    private Register dest;
    private byte addr;

    public AddressDecodedInstruction(Register dest, byte addr) {
        this.dest = dest;
        this.addr = addr;
    }

    public Register getDest() {
        return dest;
    }

    public byte getAddr() {
        return addr;
    }

    @Override
    public String toString() {
        return "<"+dest.toString()+","+getAddr()+">";
    }
}
class IntermediateResult {
    private Register dest;
    private byte value;

    public IntermediateResult(Register dest, byte value) {
        this.dest = dest;
        this.value = value;
    }

    public Register getDest() {
        return dest;
    }

    public byte getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "<"+dest.toString()+","+value+">";
    }
}
class SourceRegisterDataSet {
    private byte sourceReg1Data;
    private byte sourceReg2Data;

    public SourceRegisterDataSet(byte sourceReg1Data, byte sourceReg2Data) {
        this.sourceReg1Data = sourceReg1Data;
        this.sourceReg2Data = sourceReg2Data;
    }

    public byte getSourceReg1Data() {
        return sourceReg1Data;
    }

    public byte getSourceReg2Data() {
        return sourceReg2Data;
    }
}

/* Non-steppable Types */
interface DataMemoryRetriever {
    byte getData(byte address);
}
class DataMemory implements OutputLiner, DataMemoryRetriever {
    private byte[] mem;
    public DataMemory(int numRegs, String filename) throws IOException {
        // init memory
        mem = new byte[numRegs];
        for (int i = 0; i != mem.length; ++i) {
            mem[i] = -1;
        }

        List<String> lines = Files.readAllLines(Paths.get(filename));
        int numRegInputs;
        if (lines.get(lines.size()-1).equals("")) {
            numRegInputs = lines.size()-1;
        } else {
            numRegInputs = lines.size();
        }

        if (numRegInputs > mem.length) {
            throw new IllegalArgumentException("Too many registers in reg file");
        }

        for (int i = 0; i != numRegInputs; ++i) {
            String[] tokens = lines.get(i).split("<|,|>");
            // retrieve vals needed for instruction;
            int regNum = Integer.parseInt(tokens[1]);
            // convert to registers
            byte regData = Byte.parseByte(tokens[2]);
            mem[regNum] = regData;
        }
    }

    public String getOutputLine() {
        StringBuilder sb = new StringBuilder("DAM:");
        for (int i = 0; i < mem.length; ++i) {
            if (i != 0) {
                sb.append(",");
            }
            sb.append("<");
            sb.append(i);
            sb.append(",");
            sb.append(mem[i]);
            sb.append(">");
        }
        return sb.toString();
    }

    @Override
    public byte getData(byte address) {
        return mem[address];
    }
}

/* Extensible Types */
abstract class BasicRegister<I, O> implements Steppable, DataRetriever<O> {

    private String prefix;
    private DataRetriever<I> inSrc;

    // data needed for next compute
    private I next = null;

    // result data
    private I curr = null;

    public BasicRegister(String prefix, DataRetriever<I> inSrc) {
        this.prefix = prefix;
        this.inSrc = inSrc;
    }

    protected I getNext() {
        return next;
    }

    protected I getCurr() {
        return curr;
    }

    protected void clearCurr() {
        curr = null;
    }

    @Override
    public void fillBuffer() {
        next = inSrc.getData();
    }

    @Override
    public boolean step() {
        if (next == null) {
            return false;
        }
        curr = next;
        next = null;
        return true;
    }

    @Override
    public String getOutputLine() {
        if (curr == null) {
            return prefix + ":";
        }
        return prefix+":"+curr.toString();
    }
}


class InstructionMemory implements Steppable, DataRetriever<ValueInstruction> {

    private RegisterRetriever registerRetriever;
    private Instruction[] instructions;
    private int currInstruction = -1;
    private boolean canStep = false;
    private boolean canGetData = false;

    public InstructionMemory(RegisterRetriever registerRetriever, String filename) throws IOException {
        this.registerRetriever = registerRetriever;
        List<String> lines = Files.readAllLines(Paths.get(filename));
        int numInstructions;
        if (lines.get(lines.size()-1).equals("")) {
            numInstructions = lines.size()-1;
        } else {
            numInstructions = lines.size();
        }
        instructions = new Instruction[numInstructions];
        for (int i = 0; i != numInstructions; ++i) {
            String[] tokens = lines.get(i).split("<|,|>");
            // retrieve vals needed for instruction;
            String opcode = tokens[1];
            // convert to registers
            Register rdest = Register.fromString(tokens[2]);
            Register rsrc1 = Register.fromString(tokens[3]);
            Register rsrc2 = Register.fromString(tokens[4]);
            instructions[i] = new Instruction(opcode, rdest, rsrc1, rsrc2);
        }
    }

    @Override
    public void fillBuffer() {
        int nextInstruction = currInstruction + 1;
        if (nextInstruction < instructions.length && nextInstruction >= 0) {
            // retrieve the registers from the register file to ensure that we have the values to step.
            Byte reg1 = registerRetriever.getRegister(instructions[nextInstruction].getSource1());
            Byte reg2 = registerRetriever.getRegister(instructions[nextInstruction].getSource1());
            if (reg1 != null && reg2 != null) {
                canStep = true;
                return;
            }
        }
        canStep = false;
    }

    @Override
    public boolean step() {
        if (canStep && currInstruction < instructions.length -1) {
            ++currInstruction;
            canGetData = true;
            return true;
        }
        canGetData = false;
        return false;
    }

    public ValueInstruction getData() {
        if (canGetData && currInstruction >= 0 && currInstruction < instructions.length) {
            return new ValueInstruction(instructions[currInstruction];
        }
        return null;
    }

    @Override
    public String getOutputLine() {
        StringBuilder sb = new StringBuilder("INM:");
        for (int i = currInstruction+1; i < instructions.length; ++i) {
            if (i != currInstruction+1) {
                sb.append(",");
            }
            sb.append(instructions[i].toString());
        }
        return sb.toString();
    }
}

interface RegisterRetriever {
    Byte getRegister(Register reg); // returns value of register if avail, null if DNE.
}
class RegisterFile implements OutputLiner, RegisterRetriever{
    private byte[] vals;

    public RegisterFile(String filename) throws IOException {
        // init all valls to -1;
        vals = new byte[Register.values().length];
        for (int i = 0; i != vals.length; ++i) {
            vals[i] = -1;
        }
        // read in regs from file
        List<String> lines = Files.readAllLines(Paths.get(filename));
        for (int i = 0; i != vals.length; ++i) {
            String[] tokens = lines.get(i).split("<|,|>");
            Register reg = Register.fromString(tokens[1]);
            byte num = Byte.parseByte(tokens[2]);
            vals[reg.getIndex()] = num;
        }
    }

    @Override
    public String getOutputLine() {
        StringBuilder sb = new StringBuilder("RGF:");
        int count = 0;
        for (int i = 0; i != vals.length; ++i) {
            if (vals[i] == -1) {
                continue;
            }
            if (count > 0) {
                sb.append(",");
            }
            sb.append("<R");
            sb.append(i);
            sb.append(",");
            sb.append(vals[i]);
            sb.append(">");
            ++count;
        }
        return sb.toString();
    }

    @Override
    public Byte getRegister(Register reg) {
        byte val = vals[reg.getIndex()];
        if (val == -1) {
            return null;
        }
        return val;
    }
}

class InstructionBuffer implements Steppable {
    DataRetriever<Instruction> instructionGenerator;
    DataRetriever<SourceRegisterDataSet> registerRetriever;

    // Buffer Data

    // Generated Data
    ValueInstruction issue1Data = null;
    ValueInstruction issue2Data = null;

    public InstructionBuffer(DataRetriever<Instruction> instructionGenerator, DataRetriever<SourceRegisterDataSet> registerRetriever) {
        this.instructionGenerator = instructionGenerator;
        this.registerRetriever = registerRetriever;
    }

    @Override
    public void fillBuffer() {}

    @Override
    public boolean step() {
        // Due to bad petri net design, this is necessary here
        Instruction nextInstruction = instructionGenerator.getData();
        SourceRegisterDataSet nextRegisterData = registerRetriever.getData();

        if (nextInstruction == null || nextRegisterData == null) {
            return false;
        }
        issue1Data = null;
        issue2Data = null;
        ValueInstruction data = new ValueInstruction(nextInstruction, nextRegisterData);
        if (nextInstruction.getOpcode().equals("LD")) {
            issue2Data = data;
        } else {
            issue1Data = data;
        }
        return true;
    }

    @Override
    public String getOutputLine() {
        ValueInstruction dataToString = null;
        if (issue1Data != null && issue2Data != null) {
            throw new IllegalStateException("both issue1 and issue2 data exists");
        } else if (issue1Data != null) {
            dataToString = issue1Data;
        } else if (issue2Data != null) {
            dataToString = issue2Data;
        }
        if (dataToString == null) {
            return "INB:";
        }
        return "INB:" + dataToString.toString();

    }

    public DataRetriever<ValueInstruction> getIssue1DataRetriever() {
        return new DataRetriever<ValueInstruction>() {
            @Override
            public ValueInstruction getData() {
                return issue1Data;
            }
        };
    }

    public DataRetriever<ValueInstruction> getIssue2DataRetriever() {
        return new DataRetriever<ValueInstruction>() {
            @Override
            public ValueInstruction getData() {
                return issue2Data;
            }
        };
    }
}

class LoadInstructionBuffer extends BasicRegister<ValueInstruction, AddressDecodedInstruction> {

    public LoadInstructionBuffer(DataRetriever<ValueInstruction> inSrc) {
        super("LIB", inSrc);
    }

    @Override
    public AddressDecodedInstruction getData() {
        if (getCurr() == null) {
            return null;
        }
        AddressDecodedInstruction adi = new AddressDecodedInstruction(
                getCurr().getDest(),
                (byte)(getCurr().getRegister1Data() + getCurr().getRegister2Data())
        );
        // clear out the token when completed
        clearCurr();
        return adi;
    }
}

class AddressBuffer extends BasicRegister<AddressDecodedInstruction, IntermediateResult> {

    private DataMemoryRetriever dmr;

    public AddressBuffer(DataRetriever<AddressDecodedInstruction> inSrc, DataMemoryRetriever dmr) {
        super("ADB", inSrc);
        this.dmr = dmr;
    }

    @Override
    public IntermediateResult getData() {
        return new IntermediateResult(getCurr().getDest(), dmr.getData(getCurr().getAddr()));
    }
}