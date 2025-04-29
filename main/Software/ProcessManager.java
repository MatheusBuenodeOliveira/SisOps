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
        public IORequest pendingIO; // estado de IO do processo

        public PCB(int pid, ArrayList<Page> pages) {
            this.pid = pid;
            this.pages = pages;
            this.pc = 0;
            this.registers = new int[10];
            this.state = ProcessState.READY;
            this.pendingIO = null;
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

    public class IORequest {
        public enum IOType { READ, WRITE }

        public IOType type;
        public int address;
        public int value; // For write operations
        public int completionTime;

        public IORequest(IOType type, int address) {
            this.type = type;
            this.address = address;
            this.completionTime = 0; // Will be set when scheduled
        }

        public IORequest(IOType type, int address, int value) {
            this(type, address);
            this.value = value;
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

    // Generate a unique process ID
    private int nextPID = 1;
    private int generatePID() {
        return nextPID++;
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
            System.out.println("Scheduled process PID: " + runningProcess.pid + " PC: " + runningProcess.pc);
        } else {
            runningProcess = null;
            System.out.println("No processes to schedule");
        }
    }

    // Block the running process for I/O
    public void blockForIO(IORequest request) {
        if (runningProcess != null) {
            System.out.println("Process PID: " + runningProcess.pid + " blocked for I/O");
            runningProcess.saveContext();
            runningProcess.state = ProcessState.BLOCKED;
            runningProcess.pendingIO = request;

            // Set I/O completion time (simulated)
            int currentTime = getCurrentTime();
            request.completionTime = currentTime + 5;

            blockedQueue.add(runningProcess);
            runningProcess = null;

            // Schedule next process
            schedule();
        }
    }

    // Simulate system clock for I/O timing
    private int systemClock = 0;
    private int getCurrentTime() {
        return systemClock;
    }

    // Increment system clock
    public void tickClock() {
        systemClock++;
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
            tickClock();

            if (runningProcess != null) {
                // Vai iniciar a tread separada que manda a interrupção de relógio
                TimerInterrupt timer = new TimerInterrupt(this, TIME_SLICE);
                timer.start();

                // Coloca o processo para rodar
                System.out.println("Process PID running: " + runningProcess.pid);
                cpu.run();

                //mata a tread
                timer.stopTimer();

                // If process terminated on its own, schedule next process
                if (runningProcess != null && runningProcess.state == ProcessState.RUNNING) {
                    // Process didn't terminate but was interrupted
                    // The timer or I/O interrupt will have already scheduled the next process
                }
            } else if (!readyQueue.isEmpty()) {
                //se houver processos para escalonar escalona ele
                schedule();
            } else {
                // Just wait for I/O completions
                try {
                    Thread.sleep(100); // Small delay to avoid busy waiting
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // Handle process termination
    public void terminateRunningProcess() {
        if (runningProcess != null) {
            System.out.println("Process PID: " + runningProcess.pid + " terminated");

            // Free memory
            for (Page page : runningProcess.pages) {
                page.inUse = false;
            }

            runningProcess = null;

            // Schedule next process
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
        private ProcessManager pm;
        private int instructions;
        private boolean running;

        public TimerInterrupt(ProcessManager pm, int instructions) {
            this.pm = pm;
            this.instructions = instructions;
            this.running = true;
        }

        public void stopTimer() {
            this.running = false;
        }

        @Override
        public void run() {
            int startPC = cpu.pc;
            int instructionsExecuted = 0;

            try {
                while (running) {
                    // olhando o pc para ver se ele rodou instruções
                    int currentPC = cpu.pc;
                    if (currentPC != startPC) {
                        instructionsExecuted++;
                        startPC = currentPC;

                        // verifica se o time slice terminou
                        if (instructionsExecuted >= instructions) {
                            // Gera a interrupção de relógio
                            interruptHandler.handle(Interrupts.intTimer);
                            break;
                        }
                    }

                    // pequeno delay
                    Thread.sleep(1);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}