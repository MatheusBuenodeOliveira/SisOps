package Software;

import Hardware.*;

public class SysCallHandling {
    private HW hw;

    public SysCallHandling(HW _hw) {
        hw = _hw;
    }

    public void stop() {
        System.out.println("SYSCALL STOP");
    }

    public void handle() {
        System.out.println("SYSCALL pars: " + hw.cpu.reg[8] + " / " + hw.cpu.reg[9]);
        if (hw.cpu.reg[8] == 1) {
            // Leitura
        } else if (hw.cpu.reg[8] == 2) {
            System.out.println("OUT: " + hw.mem.pos[hw.cpu.reg[9]].p);
        } else {
            System.out.println("PARAMETRO INVALIDO");
        }
    }
}
