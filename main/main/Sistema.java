package main;

import Hardware.*;
import Software.*;
import Programs.*;

public class Sistema {
    public HW hw;
    public SO so;
    public Programs progs;

    public Sistema(int tamMem) {
        hw = new HW(tamMem);
        so = new SO(hw);
        hw.cpu.setUtilities(so.utils);
        progs = new Programs();
    }

    public void run() {
        so.utils.loadAndExec(progs.retrieveProgram("fatorialV2"));
    }

    public static void main(String args[]) {
        Sistema s = new Sistema(1024);
        s.run();
    }
}
