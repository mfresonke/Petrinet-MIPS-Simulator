package com.maxfresonke.cda4630;

public class MIPSsim {

    public static void main(String[] args) {
	// write your code here
    }
}

interface Steppable {
    void step();
}

enum Register {
    R0(0), R1(1), R2(2), R3(3), R4(4), R5(5), R6(6), R7(7);
    private int index;
    Register(int index) {
        this.index = index;
    }
}

abstract class Instruction {
    private Register dest;
    private Register source1;
    private Register source2;

    public Instruction(Register dest, Register source1, Register source2) {
        this.dest = dest;
        this.source1 = source1;
        this.source2 = source2;
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
}

class LoadInstruction extends Instruction {
    public LoadInstruction(Register dest, Register source1, Register source2) {
        super(dest, source1, source2);
    }
}

class ALUInstruction extends Instruction {
    public ALUInstruction(char op, Register dest, Register source1, Register source2) {
        super(dest, source1, source2);
        // TODO do something with 'op'
    }
}

class InstructionMemory implements Steppable {

    public InstructionMemory(){

    }

    @Override
    public void step() {

    }
}