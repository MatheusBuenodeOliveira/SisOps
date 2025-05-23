package Software;

import java.util.ArrayList;

import Hardware.*;
import Programs.Program;

public class Utilities {
    private MemoryManager memoryManager;
    private HW hw; 

    public Utilities(MemoryManager _memoryManager, HW _hw) {
        memoryManager = _memoryManager;
        hw = _hw;
    }

    public ArrayList<Page> loadProgram(Word[] p) {
        ArrayList<Page> pageList = memoryManager.alloc(p);

        if (pageList.isEmpty()) {
            System.out.println("Falha na alocação de memória. Não foi possível carregar o programa.");
            return new ArrayList<>();
        } else {
            System.out.println("Programa carregado com sucesso na memória.");
            return pageList;
        }
    }

    public void dump(Word w) {
        System.out.print("[ ");
        System.out.print(w.opc);
        System.out.print(", ");
        System.out.print(w.ra);
        System.out.print(", ");
        System.out.print(w.rb);
        System.out.print(", ");
        System.out.print(w.p);
        System.out.println("  ] ");
    }

    public void dump(int ini, int fim) {
        Word[] m = hw.mem.pos;
        for (int i = ini; i < fim; i++) {
            if(i % 8 == 0)
                System.out.println("Frame :"+ i / 8);
            System.out.print(i);
            System.out.print(":  ");
            dump(m[i] != null ? m[i] : new Word(Opcode.DATA,-1,-1,-1));
        }
    }

}
