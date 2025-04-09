package Software;

import Hardware.*;

public class SO {
    public InterruptHandling ih;
    public SysCallHandling sc;
    public Utilities utils;
    public MemoryManager memoryManager;
    public SO(HW hw) {
        ih = new InterruptHandling(hw); // rotinas de tratamento de int
        sc = new SysCallHandling(hw); // chamadas de sistema
        hw.cpu.setAddressOfHandlers(ih, sc);
        memoryManager = new MemoryManager(hw.mem);
        utils = new Utilities(memoryManager,hw);
    }
}
