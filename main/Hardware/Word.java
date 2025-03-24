package Hardware;

import Software.Opcode;

public class Word {
    public Opcode opc;
    public int ra;
    public int rb;
    public int p;

    public Word(Opcode _opc, int _ra, int _rb, int _p) {
        opc = _opc;
        ra = _ra;
        rb = _rb;
        p = _p;
    }
}
