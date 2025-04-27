package Software;

import Hardware.*;

public class SO {
    public InterruptHandling ih;
    public SysCallHandling sc;
    public Utilities utils;
    public MemoryManager memoryManager;
    public ProcessManager processManager;

    public SO(HW hw) {
        ih = new InterruptHandling(hw);
        sc = new SysCallHandling(hw);
        hw.cpu.setAddressOfHandlers(ih, sc);
        memoryManager = new MemoryManager(hw.mem);
        utils = new Utilities(memoryManager, hw);

        processManager = new ProcessManager(memoryManager, hw);
        ih.setProcessManager(processManager);
        processManager.setInterruptHandler(ih);

        sc.setProcessManager(processManager);
    }
}