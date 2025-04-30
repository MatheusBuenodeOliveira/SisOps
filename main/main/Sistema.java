package main;

import Hardware.*;
import Software.*;
import Programs.*;

public class Sistema {
    public HW hw;
    public SO so;
    public Programs progs;
    private ConsoleThread consoleThread;

    public Sistema(int tamMem) {
        hw = new HW(tamMem);
        so = new SO(hw);
        hw.cpu.setUtilities(so.utils);
        progs = new Programs();
    }

    public void run() {
        System.out.println("Iniciando SisOps - Sistema Operacional Simulado");

        // Inicia a thread do console para receber comandos do usuário
        consoleThread = new ConsoleThread(this, progs, so.processManager);
        consoleThread.start();

        try {
            // Aguarda a thread do console terminar (quando o usuário digitar "exit")
            consoleThread.join();
        } catch (InterruptedException e) {
            System.err.println("Erro ao aguardar término da thread do console: " + e.getMessage());
        }

        System.out.println("Sistema encerrado.");
    }

    public static void main(String args[]) {
        Sistema s = new Sistema(1024);
        s.run();
    }
}