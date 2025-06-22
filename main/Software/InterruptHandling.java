package Software;

import Hardware.*;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class InterruptHandling {
    private HW hw;
    private ProcessManager processManager;

    public InterruptHandling(HW _hw) {
        hw = _hw;
    }

    public void setProcessManager(ProcessManager pm) {
        this.processManager = pm;
    }

    public void handle(ConcurrentLinkedQueue<Interrupts> irpt) {
        for (Interrupts intrp : irpt){
            System.out.println("Interrupcao " + irpt + "   pc: " + hw.cpu.pc);

            if (intrp == Interrupts.intTimer && processManager != null) {
                processManager.handleTimerInterrupt();
            }
            if(intrp == Interrupts.IOReturn){
                processManager.unblockProcessFromIO(hw.cpu.ReturningOfIO.poll());
            }
            irpt.remove(intrp);
        }
    }

}
