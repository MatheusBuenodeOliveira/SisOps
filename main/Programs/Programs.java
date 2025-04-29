package Programs;

import Hardware.*;
import Software.Opcode;

public class Programs {
    public Program retrieveProgram(String pname) {
        for (Program p : progs) {
            if (p != null && p.name.equals(pname)){
				return p;
			}
                
        }
        return null;
    }

    public Program[] progs = new Program[]{
            new Program("fatorialV2",
                    new Word[]{
                            new Word(Opcode.LDI, 0, -1, 5), // numero para colocar na memoria, ou pode ser lido
                            new Word(Opcode.STD, 0, -1, 19),
                            new Word(Opcode.LDD, 0, -1, 19),
                            new Word(Opcode.LDI, 1, -1, -1),
                            new Word(Opcode.LDI, 2, -1, 13), // SALVAR POS STOP
                            new Word(Opcode.JMPIL, 2, 0, -1), // caso negativo pula pro STD
                            new Word(Opcode.LDI, 1, -1, 1),
                            new Word(Opcode.LDI, 6, -1, 1),
                            new Word(Opcode.LDI, 7, -1, 13),
                            new Word(Opcode.JMPIE, 7, 0, 0), // POS 9 pula para STD (Stop-1)
                            new Word(Opcode.MULT, 1, 0, -1),
                            new Word(Opcode.SUB, 0, 6, -1),
                            new Word(Opcode.JMP, -1, -1, 9), // pula para o JMPIE
                            new Word(Opcode.STD, 1, -1, 18),
                            new Word(Opcode.LDI, 8, -1, 2), // escrita
                            new Word(Opcode.LDI, 9, -1, 18), // endereco com valor a escrever
                            new Word(Opcode.SYSCALL, -1, -1, -1),
                            new Word(Opcode.STOP, -1, -1, -1), // POS 17
                            new Word(Opcode.DATA, -1, -1, -1), // POS 18
                            new Word(Opcode.DATA, -1, -1, -1)} // POS 19
            ),
			new Program("testeprog",
					new Word[]{
							new Word(Opcode.LDI, 0, -1, 5),
							new Word(Opcode.LDI, 0, -1, 5),
							new Word(Opcode.LDI, 0, -1, 5),
							new Word(Opcode.LDI, 0, -1, 5),
							new Word(Opcode.LDI, 0, -1, 5),
							new Word(Opcode.LDI, 0, -1, 5),
							new Word(Opcode.LDI, 0, -1, 5),
							new Word(Opcode.LDI, 0, -1, 5),
							new Word(Opcode.LDI, 0, -1, 5),
							new Word(Opcode.LDI, 0, -1, 5),
							new Word(Opcode.STOP, -1, -1, -1)
					}
			)

    };
}
