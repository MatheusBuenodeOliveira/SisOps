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
        for (Interrupts intrp : irpt) {
            System.out.println("Interrupcao " + intrp + "   pc: " + hw.cpu.pc);

            switch (intrp) {
                case intTimer:
                    if (processManager != null) {
                        processManager.handleTimerInterrupt();
                    }
                    break;

                case IOReturn:
                    if (processManager != null) {
                        ProcessManager.PCB returningFromIO = hw.cpu.ReturningOfIO.poll();
                        processManager.unblockProcessFromIO(returningFromIO);
                    }
                    break;

                case PageFault:
                    if (processManager != null) {
                        processManager.handlePageFault();
                    }
                    break;

                case intEnderecoInvalido:
                    System.out.println("ERRO: Endereço de memória inválido!");
                    if (processManager != null) {
                        processManager.terminateRunningProcess();
                    }
                    break;

                case intInstrucaoInvalida:
                    System.out.println("ERRO: Instrução inválida!");
                    if (processManager != null) {
                        processManager.terminateRunningProcess();
                    }
                    break;

                case intOverflow:
                    System.out.println("ERRO: Overflow aritmético!");
                    if (processManager != null) {
                        processManager.terminateRunningProcess();
                    }
                    break;

                case intSTOP:
                    System.out.println("Instrução STOP executada.");
                    if (processManager != null) {
                        processManager.terminateRunningProcess();
                    }
                    break;

                default:
                    System.out.println("Interrupção não tratada: " + intrp);
                    break;
            }

            irpt.remove(intrp);
        }
    }
}