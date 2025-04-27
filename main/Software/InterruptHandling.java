package Software;

import Hardware.*;

public class InterruptHandling {
    private HW hw;
    private ProcessManager processManager;

    public InterruptHandling(HW _hw) {
        hw = _hw;
    }

    public void setProcessManager(ProcessManager pm) {
        this.processManager = pm;
    }

    public void handle(Interrupts irpt) {
        System.out.println("Interrupcao " + irpt + "   pc: " + hw.cpu.pc);

        if (irpt == Interrupts.intTimer && processManager != null) {
            // Handle timer interrupt by telling the process manager
            processManager.handleTimerInterrupt();
        }
    }

}
