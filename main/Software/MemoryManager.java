package Software;

import java.util.HashMap;

import Hardware.Memory;

public class MemoryManager {
    HashMap<Integer, Page> pageList = new HashMap<>();
    
    public MemoryManager(Memory mem){
        int j =0;
        for(int i =0; i < mem.pos.length; i+=32){
            pageList.put(j, new Page(i, i+32, 32, false));
            j++;
        }
    }
}
