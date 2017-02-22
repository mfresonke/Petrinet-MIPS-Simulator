package com.maxfresonke.cda4630;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class MIPSsim {

    private static final String FILENAME_INPUT_INSTRUCTIONS = "instructions.txt";
    private static final String FILENAME_INPUT_REGISTER_FILE = "registers.txt";

    public static void main(String[] args) throws IOException {
        InstructionMemory inm = new InstructionMemory(FILENAME_INPUT_INSTRUCTIONS);
        RegisterFile rgf = new RegisterFile(inm, FILENAME_INPUT_REGISTER_FILE);
        InstructionBuffer inb = new InstructionBuffer(inm, rgf);
        LoadInstructionBuffer lib = new LoadInstructionBuffer(inb);

        Steppable[] steps = {
            inm, inb, lib, rgf,
        };

        StringBuilder output = new StringBuilder();

        boolean stepsLeft = false;
        int count = 0;
        do {
            // output current iteration
            output.append("STEP " + count + ":" + "\n");
            for (Steppable s : steps) {
                output.append(s.toString());
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

interface Steppable {
    void fillBuffer();
    boolean step(); // Returns true if was able to step
    String toString(); // while redundant, needs to output it's own line.
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


interface InstructionGenerator {
    Instruction getData();
}
class SourceRegisterSet {
    private Register source1;
    private Register source2;

    public SourceRegisterSet(Register source1, Register source2) {
        this.source1 = source1;
        this.source2 = source2;
    }

    public Register getSource1() {
        return source1;
    }

    public Register getSource2() {
        return source2;
    }
}
interface InstructionTopLevelSourceRegisterSetGenerator {
    SourceRegisterSet getTopLevelSourceRegisterSet();
}
class InstructionMemory implements Steppable, InstructionGenerator, InstructionTopLevelSourceRegisterSetGenerator {

    private Instruction[] instructions;
    private int currInstruction = -1;

    public InstructionMemory(String filename) throws IOException {
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
        // do nothing since we are our own source of data.
    }

    @Override
    public boolean step() {
        if (currInstruction < instructions.length -1) {
            ++currInstruction;
            return true;
        }
        return false;
    }

    public Instruction getData() {
        if (currInstruction >= 0 && currInstruction < instructions.length) {
            return instructions[currInstruction];
        }
        return null;
    }

    // magical method that can travel through time
    public SourceRegisterSet getTopLevelSourceRegisterSet() {
        if (currInstruction >= -1 && currInstruction < instructions.length-1) {
            return new SourceRegisterSet(
                    instructions[currInstruction+1].getSource1(),
                    instructions[currInstruction+1].getSource2()
            );
        }
        return null;
    }

    @Override
    public String toString() {
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
interface SourceRegisterDataSetRetriever {
    SourceRegisterDataSet getData();
}
class RegisterFile implements Steppable, SourceRegisterDataSetRetriever {
    byte[] vals;
    InstructionTopLevelSourceRegisterSetGenerator srcRegGen;

    byte sourceReg1Val = -1;
    byte sourceReg2Val = -1;


    public RegisterFile(InstructionTopLevelSourceRegisterSetGenerator srcRegGen, String filename) throws IOException {
        this.srcRegGen = srcRegGen;
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
    public String toString() {
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
    public void fillBuffer() {
        sourceReg1Val = -1;
        sourceReg2Val = -1;
        SourceRegisterSet srs = srcRegGen.getTopLevelSourceRegisterSet();
        if (srs != null) {
            sourceReg1Val = vals[srs.getSource1().getIndex()];
            sourceReg2Val = vals[srs.getSource2().getIndex()];
        }
    }

    @Override
    public boolean step() {
        if (sourceReg1Val == -1 || sourceReg2Val == -1) {
            return false;
        }
        return true;
    }

    @Override
    public SourceRegisterDataSet getData() {
        if (sourceReg1Val != -1 && sourceReg2Val != -1) {
            return new SourceRegisterDataSet(sourceReg1Val, sourceReg2Val);
        }
        return null;
    }
}

interface Issue1ValueInstructionRetriever {
    ValueInstruction getIssue1Data();
}
interface Issue2ValueInstructionRetriever {
    ValueInstruction getIssue2Data();
}
class InstructionBuffer implements Steppable, Issue1ValueInstructionRetriever, Issue2ValueInstructionRetriever {
    InstructionGenerator instructionGenerator;
    SourceRegisterDataSetRetriever registerRetriever;

    // Buffer Data

    // Generated Data
    ValueInstruction issue1Data = null;
    ValueInstruction issue2Data = null;

    public InstructionBuffer(InstructionGenerator instructionGenerator, SourceRegisterDataSetRetriever registerRetriever) {
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
    public String toString() {
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

    @Override
    public ValueInstruction getIssue1Data() {
        return issue1Data;
    }

    @Override
    public ValueInstruction getIssue2Data() {
        return issue2Data;
    }
}

interface DataRetriever<O> {
    O getData();
}

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
    public String toString() {
        if (curr == null) {
            return prefix + ":";
        }
        return prefix+":"+curr.toString();
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

class AddressBuffer implements Steppable {

    private DecodedAddressGenerator dag;

    // buffer data
    AddressDecodedInstruction nextAddressDecodedInstruction = null;

    // current data
    AddressDecodedInstruction nextAddressDecodedInstruction = null;

    public AddressBuffer(DecodedAddressGenerator dag) {
        this.dag = dag;
    }

    @Override
    public void fillBuffer() {
        nextAddressDecodedInstruction = dag.getData();
    }

    @Override
    public boolean step() {
        return false;
    }

    @Override
    public String toString() {
        return super.toString();
    }
}