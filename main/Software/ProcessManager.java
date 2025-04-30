package Software;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import Hardware.*;
import Programs.Program;

public class ProcessManager {
    private Queue<PCB> readyQueue;
    private Queue<PCB> blockedQueue; // Queue for processes waiting on I/O
    private PCB runningProcess;
    private MemoryManager memoryManager;
    private CPU cpu;
    private HW hw;
    private InterruptHandling interruptHandler;
    private volatile boolean schedulerRunning = true;

    // Para sincronização entre threads
    private final Lock processLock = new ReentrantLock();
    private SchedulerThread schedulerThread;

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

    public void startSchedulerThread() {
        this.schedulerThread = new SchedulerThread(this);
        this.schedulerThread.start();
    }

    public void shutdownScheduler() {
        schedulerRunning = false;
        if (schedulerThread != null) {
            schedulerThread.stopScheduler();
            try {
                schedulerThread.join(1000); // Espera pelo término da thread por até 1 segundo
            } catch (InterruptedException e) {
                System.err.println("Erro ao aguardar término do escalonador: " + e.getMessage());
            }
        }
    }

    // Process Control Block to store process state
    public class PCB {
        public int pid; //Id unico do processo
        public int pc; //programcounter do ultimo running
        public ArrayList<Page> pages; // lista de páginas do processo
        public int[] registers; // registradores da última vez que ele rodou
        public ProcessState state; // estado atual do processo
        public String programName; // Nome do programa

        public PCB(int pid, ArrayList<Page> pages, String programName) {
            this.pid = pid;
            this.pages = pages;
            this.pc = 0;
            this.registers = new int[10];
            this.state = ProcessState.READY;
            this.programName = programName;
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
        try {
            processLock.lock();
            ArrayList<Page> pages = memoryManager.alloc(program.image);
            if (pages.isEmpty()) {
                System.out.println("Falha em alocar memória de um processo: " + program.name);
                return null;
            }

            PCB pcb = new PCB(generatePID(), pages, program.name);
            readyQueue.add(pcb);
            System.out.println("Process criado com PID: " + pcb.pid + " - " + program.name);
            return pcb;
        } finally {
            processLock.unlock();
        }
    }

    // Generate a unique process ID
    private int nextPID = 1;
    private int generatePID() {
        return nextPID++;
    }

    // ESCALONA O NOVO PROCESSO (Round Robin)
    public void schedule() {
        try {
            processLock.lock();

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
                //MUDA O STATUS PARA RUNNING
                runningProcess.state = ProcessState.RUNNING;
                //CARREGA O CONTEXTO NA CPU
                runningProcess.loadContext();
                System.out.println("Scheduled process PID: " + runningProcess.pid + " PC: " + runningProcess.pc);
            } else {
                runningProcess = null;
                System.out.println("No processes to schedule");
            }
        } finally {
            processLock.unlock();
        }
    }


    // Handle - interupção de relógio
    public void handleTimerInterrupt() {
        try {
            processLock.lock();
            System.out.println("Interrupção de relógio " + runningProcess.pid + "- troca de contexto");
            schedule();
        } finally {
            processLock.unlock();
        }
    }

    // Ciclo principal do escalonador - chamado continuamente pela thread do escalonador
    public void schedulerCycle() {
        try {
            processLock.lock();

            // Se não houver processo em execução, mas houver processos prontos, escalona um
            if (runningProcess == null && !readyQueue.isEmpty()) {
                schedule();
            }

            // Se houver um processo em execução, executa uma quantidade limitada de instruções
            if (runningProcess != null) {
                // Inicia a thread separada que monitora o tempo de execução
                TimerInterrupt timer = new TimerInterrupt();
                timer.start();

                // Coloca o processo para rodar
                System.out.println("Process PID running: " + runningProcess.pid);
                cpu.run();

                // Para a thread do timer
                timer.stopTimer();
            }
        } finally {
            processLock.unlock();
        }
    }

    // Teste se há processos que podem ser escalonados
    public boolean hasProcessesToSchedule() {
        try {
            processLock.lock();
            return runningProcess != null || !readyQueue.isEmpty() || !blockedQueue.isEmpty();
        } finally {
            processLock.unlock();
        }
    }

    // Lista todos os processos no sistema
    public void listProcesses() {
        try {
            processLock.lock();

            System.out.println("PID\tEstado\t\tPrograma\tPC");
            System.out.println("--------------------------------------------");

            // Processo em execução
            if (runningProcess != null) {
                System.out.printf("%d\t%s\t%s\t\t%d%n",
                        runningProcess.pid,
                        runningProcess.state,
                        runningProcess.programName,
                        runningProcess.pc);
            }

            // Processos prontos
            for (PCB pcb : readyQueue) {
                System.out.printf("%d\t%s\t\t%s\t\t%d%n",
                        pcb.pid,
                        pcb.state,
                        pcb.programName,
                        pcb.pc);
            }

            // Processos bloqueados
            for (PCB pcb : blockedQueue) {
                System.out.printf("%d\t%s\t%s\t\t%d%n",
                        pcb.pid,
                        pcb.state,
                        pcb.programName,
                        pcb.pc);
            }

            if (runningProcess == null && readyQueue.isEmpty() && blockedQueue.isEmpty()) {
                System.out.println("Nenhum processo no sistema.");
            }
        } finally {
            processLock.unlock();
        }
    }

    // Mostra o status da memória
    public void showMemoryStatus() {
        try {
            processLock.lock();

            int totalPages = 0;
            int usedPages = 0;

            for (Page page : memoryManager.pageList) {
                totalPages++;
                if (page.inUse) usedPages++;
            }

            System.out.println("Total de páginas: " + totalPages);
            System.out.println("Páginas em uso: " + usedPages);
            System.out.println("Páginas livres: " + (totalPages - usedPages));
            System.out.printf("Utilização: %.2f%%%n", ((float)usedPages / totalPages) * 100);
        } finally {
            processLock.unlock();
        }
    }

    // Mata um processo específico pelo PID
    public boolean killProcess(int pid) {
        try {
            processLock.lock();

            // Verifica se é o processo em execução
            if (runningProcess != null && runningProcess.pid == pid) {
                terminateRunningProcess();
                return true;
            }

            // Verifica na fila de prontos
            PCB toRemove = null;
            for (PCB pcb : readyQueue) {
                if (pcb.pid == pid) {
                    toRemove = pcb;
                    break;
                }
            }

            if (toRemove != null) {
                readyQueue.remove(toRemove);
                freeProcessMemory(toRemove);
                System.out.println("Processo com PID " + pid + " removido da fila de prontos.");
                return true;
            }

            // Verifica na fila de bloqueados
            toRemove = null;
            for (PCB pcb : blockedQueue) {
                if (pcb.pid == pid) {
                    toRemove = pcb;
                    break;
                }
            }

            if (toRemove != null) {
                blockedQueue.remove(toRemove);
                freeProcessMemory(toRemove);
                System.out.println("Processo com PID " + pid + " removido da fila de bloqueados.");
                return true;
            }

            return false;
        } finally {
            processLock.unlock();
        }
    }

    // Libera a memória usada por um processo
    private void freeProcessMemory(PCB process) {
        for (Page page : process.pages) {
            page.inUse = false;
        }
    }

    //get de processo por id
    public PCB getProcess(int pid) {
        PCB toReturn = null;
        if (runningProcess != null && runningProcess.pid == pid) {
            toReturn = runningProcess;
        }
        for (PCB pcb : readyQueue) {
            if (pcb.pid == pid) {
                toReturn = pcb;
            }
        }
        return toReturn;
    }

    // Handle process termination
    public void terminateRunningProcess() {
        try {
            processLock.lock();

            if (runningProcess != null) {
                System.out.println("Process PID: " + runningProcess.pid + " terminated");

                // Free memory
                freeProcessMemory(runningProcess);

                runningProcess = null;
            }
        } finally {
            processLock.unlock();
        }
    }

    // Verifica se tem algum processo ativo / admitido
    public boolean hasActiveProcesses() {
        try {
            processLock.lock();
            return runningProcess != null || !readyQueue.isEmpty() || !blockedQueue.isEmpty();
        } finally {
            processLock.unlock();
        }
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
                while (running) {
                    //Thread.sleep(0,1);
                    Thread.sleep(5000);
                    cpu.setInterupt(Interrupts.intTimer);
                    break;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}