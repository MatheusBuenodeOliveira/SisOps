package Hardware;

import Software.Opcode;

public class Memory {
    public Word[] pos;

    public Memory(int size) {
        pos = new Word[size];
        for (int i = 0; i < pos.length; i++) {
            pos[i] = new Word(Opcode.___, -1, -1, -1);
        }
    }
}
