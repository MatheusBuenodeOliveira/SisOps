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
        so.processManager.createProcess(progs.retrieveProgram("fatorialV2"));
        so.processManager.createProcess(progs.retrieveProgram("fatorialV2"));

        System.out.println("Starting execution with Round Robin scheduler");
        so.processManager.run();

        System.out.println("All processes completed");
    }

    public static void main(String args[]) {
        Sistema s = new Sistema(1024);
        s.run();
    }
}