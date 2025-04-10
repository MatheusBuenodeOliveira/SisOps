package Software;

import java.util.ArrayList;
import Programs.Program;
import Hardware.*;

public class MemoryManager {
    ArrayList<Page> pageList = new ArrayList<>();
    Memory mem;

    public MemoryManager(Memory mem){
        for(int i = 0; i < mem.pos.length; i+=8){
            // if(i == 0){
            //     pageList.add(new Page(i, i+8, 8, true));
            // }else
            // {
                pageList.add(new Page(i, i+8, 8, false));
            //}
        }
        this.mem = mem;
    }

    public ArrayList<Page> alloc(Word[] p){
        int programSize = p.length;
        int restProgramSize = 0;
        ArrayList<Page> myProgramPages = new ArrayList<>();

        for(Page pg : pageList){
            if(pg.inUse == false){
                for(int i = 0; i < 8; i++){
                    if (restProgramSize < programSize) {
                        mem.pos[pg.pageStart + i] = p[restProgramSize];
                        restProgramSize++;
                    }
                }
                pg.inUse = true;
                myProgramPages.add(pg);
            }
        }

        if(restProgramSize != programSize){
            for (Page page : myProgramPages) {
                page.inUse = false;
            }
    
            return new ArrayList<>();
        }
    
        return myProgramPages;
    }
    
}
