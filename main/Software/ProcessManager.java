package Software;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import Hardware.*;
import Programs.Program;

public class ProcessManager {
    private static final int TIME_SLICE = 1; // Quantum for Round Robin (instructions)

    private Queue<PCB> readyQueue;
    private Queue<PCB> blockedQueue; // Queue for processes waiting on I/O
    private PCB runningProcess;
    private MemoryManager memoryManager;
    private CPU cpu;
    private HW hw;
    private InterruptHandling interruptHandler;

    public ProcessManager(MemoryManager memoryManager, HW hw) {
        this.memoryManager = memoryManager;
        this.hw = hw;
        this.cpu = hw.cpu;
        this.readyQueue = new LinkedList<>();
        this.blockedQueue = new LinkedList<>();
    }

    public void setInterruptHandler(InterruptHandling ih) {
        this.interruptHandler = ih;
    }

    // Process Control Block to store process state
    public class PCB {
        public int pid; //Id unico do processo
        public int pc; //programcounter do ultimo running
        public ArrayList<Page> pages; // lista de páginas do processo
        public int[] registers; // registradores da última vez que ele rodou
        public ProcessState state; // estado atual do processo

        public PCB(int pid, ArrayList<Page> pages) {
            this.pid = pid;
            this.pages = pages;
            this.pc = 0;
            this.registers = new int[10];
            this.state = ProcessState.READY;
        }

        public void saveContext() {
            this.pc = cpu.pc;
            for (int i = 0; i < cpu.reg.length; i++) {
                this.registers[i] = cpu.reg[i];
            }
        }

        public void loadContext() {
            //carrega os registradores
            for (int i = 0; i < this.registers.length; i++) {
                cpu.reg[i] = this.registers[i];
            }
            cpu.setContext(this.pages, this.pc);
        }
    }

    public enum ProcessState {
        NEW, READY, RUNNING, BLOCKED, TERMINATED
    }

    // Cria um processo para o programa
    public PCB createProcess(Program program) {
        ArrayList<Page> pages = memoryManager.alloc(program.image);
        if (pages.isEmpty()) {
            System.out.println("Failed to allocate memory for process: " + program.name);
            return null;
        }

        PCB pcb = new PCB(generatePID(), pages);
        readyQueue.add(pcb);
        System.out.println("Process created with PID: " + pcb.pid + " - " + program.name);
        return pcb;
    }

    //Executa um processo especifico
    public void executeProcess(int pid) {
        PCB process = getProcess(pid);
        if (process == null) {
            System.out.println("Process not found: " + pid);
            return;
        }

        // Salva o contexto do processo atual
        if (runningProcess != null) {
            runningProcess.saveContext();
            if (runningProcess.state == ProcessState.RUNNING) {
                runningProcess.state = ProcessState.READY;
                readyQueue.add(runningProcess);
            }
        }

        // remove ele da fila de pronto pois agora ta em state running
        readyQueue.remove(process);

        // Setta o processo para rodar
        runningProcess = process;
        runningProcess.state = ProcessState.RUNNING;
        runningProcess.loadContext();

        // roda ele
        cpu.run();

        // coloca ele na fila de prontos
        if (runningProcess != null && runningProcess.state == ProcessState.RUNNING) {
            runningProcess.saveContext();
            runningProcess.state = ProcessState.READY;
            readyQueue.add(runningProcess);
            runningProcess = null;
        }
    }

    // Generate a unique process ID
    private int nextPID = 1;
    private int generatePID() {
        return nextPID++;
    }

    //pega um processo especifico
    public PCB getProcess(int pid) {
        for (PCB pcb : readyQueue) {
            if (pcb.pid == pid) {
                return pcb;
            }
        }
        return null;
    }

    //PEGA TODOS OS PROCESSOS
    public ArrayList<PCB> getAllProcesses() {
        var temp = new ArrayList<PCB>(readyQueue);

        if (runningProcess != null)
            temp.add(runningProcess);

        return temp;
    }

    // REMOVER PROCESSO
    public boolean removeProcess(int pid) {
        PCB processToRemove = null;

        // VERIFICA SE TA RODANDO
        if (runningProcess != null && runningProcess.pid == pid) {
            processToRemove = runningProcess;
            runningProcess = null;
        } else {
            // PROCURA NA READY QUEUE
            for (PCB pcb : readyQueue) {
                if (pcb.pid == pid) {
                    processToRemove = pcb;
                    readyQueue.remove(pcb);
                    break;
                }
            }

            // If not found, check blocked queue
            if (processToRemove == null) {
                for (PCB pcb : blockedQueue) {
                    if (pcb.pid == pid) {
                        processToRemove = pcb;
                        blockedQueue.remove(pcb);
                        break;
                    }
                }
            }
        }

        if (processToRemove != null) {
            // Free memory
            for (Page page : processToRemove.pages) {
                page.inUse = false;
            }
            return true;
        }

        return false;
    }

    // ESCALONA O NOVO PROCESSO (Round Robin)
    public void schedule() {
        if (runningProcess != null) {
            // SALVA O CONTEXTO DO PROCESSO ATUAL NO PCB
            runningProcess.saveContext();
            //ALTERA O PROCESSO PARA PRONTO E MOVO PARA A FILA DE PRONTOS
            if (runningProcess.state == ProcessState.RUNNING) {
                runningProcess.state = ProcessState.READY;
                readyQueue.add(runningProcess);
            }
        }

        // PEGA O PROXIMO PROCESSO DA FILA
        if (!readyQueue.isEmpty()) {
            runningProcess = readyQueue.poll();
            //MUDA O STATUS PARA RUNNINGS
            runningProcess.state = ProcessState.RUNNING;
            //CARREGA O CONTEXTO NA CPU
            runningProcess.loadContext();
            //readyQueue.remove(runningProcess);
            System.out.println("Scheduled process PID: " + runningProcess.pid + " PC: " + runningProcess.pc);
        } else {
            runningProcess = null;
            System.out.println("No processes to schedule");
        }
    }

    // Handle timer interrupt - called from InterruptHandling
    public void handleTimerInterrupt() {
        System.out.println("Timer interrupt "+runningProcess.pid+"- context switch");
        schedule();
    }

    // Run the system
    public void run() {
        //COMEÇAR O PRIMEIRO PROCESSO
        if (runningProcess == null && !readyQueue.isEmpty()) {
            schedule();
        }
        // RODAR O PROCESSO ATÉ TERMINAR OU RECEBER UMA INTERRUPÇÃO
        while (hasActiveProcesses()) {

            if (runningProcess != null) {
                // Vai iniciar a tread separada que manda a interrupção de relógio
                TimerInterrupt timer = new TimerInterrupt();
                timer.start();

                // Coloca o processo para rodar
                System.out.println("Process PID running: " + runningProcess.pid);
                cpu.run();

                //mata a tread
                timer.stopTimer();

            } else if (!readyQueue.isEmpty()) {
                //se houver processos para escalonar escalona ele
                schedule();
            }
        }
    }

    // termina o processo
    public void terminateRunningProcess() {
        if (runningProcess != null) {
            System.out.println("Process PID: " + runningProcess.pid + " terminated");

            removeProcess(runningProcess.pid);

            if (!readyQueue.isEmpty()) {
                schedule();
            }
        }
    }

    // Verifica se tem algum processo ativo / admitido
    public boolean hasActiveProcesses() {
        return runningProcess != null || !readyQueue.isEmpty() || !blockedQueue.isEmpty();
    }

    private class TimerInterrupt extends Thread {

        private boolean running;

        public TimerInterrupt() {
            this.running = true;
        }

        public void stopTimer() {
            this.running = false;
        }

        @Override
        public void run() {
            try {
                // Simply wait for the specified time slice
                Thread.sleep(TIME_SLICE);

                // If still running after the sleep, generate the timer interrupt
                if (running) {
                    // Gera a interrupção de relógio
                    cpu.setInterupt(Interrupts.intTimer);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}