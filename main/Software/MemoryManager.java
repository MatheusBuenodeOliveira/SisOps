package Software;

import java.util.ArrayList;
import Programs.Program;
import Hardware.*;

public class MemoryManager {
    ArrayList<Page> pageList = new ArrayList<>();
    Memory mem;

    public MemoryManager(Memory mem){
        for(int i = 0; i < mem.pos.length; i+=8){
                pageList.add(new Page(i, i+8, 8, false));
        }
        this.mem = mem;
    }

    public ArrayList<Page> alloc(Word[] p) {
        int programSize = p.length;
        int requiredPages = (int) Math.ceil(programSize / 8.0);
        ArrayList<Page> myProgramPages = new ArrayList<>();

        int freePages = 0;
        for(Page pg : pageList) {
            if(!pg.inUse) freePages++;
        }

        if(freePages < requiredPages) {
            System.out.println("Sem páginas suficientes, preciso de" + requiredPages + " pages, mas temos apenas " + freePages + " disponiveis");
            return new ArrayList<>();
        }

        int loadedWords = 0;
        for(Page pg : pageList) {
            if(!pg.inUse && loadedWords < programSize) {
                pg.inUse = true;
                myProgramPages.add(pg);

                for(int i = 0; i < pg.size && loadedWords < programSize; i++) {
                    mem.pos[pg.pageStart + i] = p[loadedWords++];
                }
            }
        }

        return myProgramPages;
    }
    
}
