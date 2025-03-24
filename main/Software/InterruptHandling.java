package Software;

import Hardware.*;

public class InterruptHandling {
    private HW hw;

    public InterruptHandling(HW _hw) {
        hw = _hw;
    }

    public void handle(Interrupts irpt) {
        System.out.println("Interrupcao " + irpt + "   pc: " + hw.cpu.pc);
    }
}
