package Software;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import Hardware.*;
import Programs.Program;
import Programs.Programs;

public class ProcessManager {
    private ConcurrentLinkedQueue<PCB> readyQueue;
    public ConcurrentLinkedQueue<PCB> blockedQueue; // Queue for processes waiting on I/O
    private PCB runningProcess;
    private PCB nopProcess; // Processo NOP dedicado
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
        this.readyQueue = new ConcurrentLinkedQueue<>();
        this.blockedQueue = new ConcurrentLinkedQueue<>();
        this.nopProcess = null;

        // Cria o processo NOP na inicialização
        createNopProcess();
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

    /**
     * Cria o processo NOP que será usado quando não houver outros processos
     */
    private void createNopProcess() {
        try {
            processLock.lock();

            Program nopProgram = new Programs().retrieveProgram("nop");
            if (nopProgram != null) {
                // Aloca páginas para o processo NOP
                ArrayList<Page> nopPages = memoryManager.alloc(nopProgram.image, "NOP", -1);
                if (!nopPages.isEmpty()) {
                    nopProcess = new PCB(-1, nopPages, "NOP");
                    nopProcess.state = ProcessState.READY;
                    System.out.println("Processo NOP criado e pronto para uso quando necessário.");
                } else {
                    System.err.println("Erro: Não foi possível alocar memória para o processo NOP!");
                }
            } else {
                System.err.println("Erro: Programa NOP não encontrado!");
            }
        } finally {
            processLock.unlock();
        }
    }

    public void unblockProcessFromIO(PCB found) {
        try {
            processLock.lock();
            blockedQueue.remove(found);
            found.isWaitingIORequest = false;
            found.state = ProcessState.READY;
            readyQueue.add(found);

            // Se estava rodando NOP e agora tem processo real, força nova escalonação
            if (runningProcess != null && runningProcess.pid == -1 && !readyQueue.isEmpty()) {
                System.out.println("Processo desbloqueado - interrompendo NOP para escalonar processo real");
                schedule();
            }
        } finally {
            processLock.unlock();
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
        public boolean isWaitingIORequest;
        public int IOReturnAddress;
        public int IOReturnValue;
        public boolean PendingPageUpdate;

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
            cpu.ProcessName = this.programName;

            if(PendingPageUpdate){
                hw.mem.pos[cpu.getMemAddr(this.IOReturnAddress)].p = this.IOReturnValue;

                this.PendingPageUpdate = false;
            }
        }
    }

    public enum ProcessState {
        NEW, READY, RUNNING, BLOCKED, TERMINATED
    }

    // Cria um processo para o programa
    public PCB createProcess(Program program) {
        try {
            processLock.lock();
            int pid = generatePID();

            // Cria template no disco primeiro
            hw.disk.createProcessFromTemplate(pid, program.name);

            // Aloca páginas virtuais usando PID
            ArrayList<Page> pages = memoryManager.alloc(program.image, program.name, pid);
            if (pages.isEmpty()) {
                System.out.println("Falha em alocar memória de um processo: " + program.name);
                return null;
            }

            PCB pcb = new PCB(pid, pages, program.name);
            readyQueue.add(pcb);
            System.out.println("Process criado com PID: " + pcb.pid + " - " + program.name);

            // Se estava rodando NOP, força nova escalonação para dar prioridade ao processo real
            if (runningProcess != null && runningProcess.pid == -1) {
                System.out.println("Novo processo adicionado - interrompendo NOP");
                // Marca para reescalonamento na próxima oportunidade
                cpu.setInterupt(Interrupts.intTimer);
            }

            return pcb;
        } finally {
            processLock.unlock();
        }
    }

    public void handlePageFault() {
        PCB running = runningProcess;
        if (running == null) return;

        System.out.println("Page Fault! Bloqueando processo " + running.programName + " (PID: " + running.pid + ")");

        // Remove da CPU
        runningProcess.saveContext();
        running.state = ProcessState.BLOCKED;
        runningProcess = null;
        blockedQueue.add(running);

        int faultAddr = hw.cpu.pagedFaultedAdress;
        int virtualPageNumber = faultAddr / 8;

        // Simula IO para recarregar a página
        new Thread(() -> {
            try {
                Thread.sleep(10000); // simula tempo de I/O
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // Após "I/O", carrega página na memória
            memoryManager.handlePageFault(running.pid, running.programName, virtualPageNumber);

            // Retorna o processo ao CPU via fila especial
            hw.cpu.ReturningOfIO.add(running);
            hw.cpu.setInterupt(Interrupts.IOReturn);
        }).start();
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

            // Salva contexto do processo atual se existir
            if (runningProcess != null) {
                runningProcess.saveContext();

                // Se é um processo real (não NOP) e ainda está rodando, volta para fila
                if (runningProcess.pid != -1 && runningProcess.state == ProcessState.RUNNING) {
                    runningProcess.state = ProcessState.READY;
                    readyQueue.add(runningProcess);
                }
            }

            // Prioriza processos reais sobre NOP
            if (!readyQueue.isEmpty()) {
                runningProcess = readyQueue.poll();
                runningProcess.state = ProcessState.RUNNING;
                runningProcess.loadContext();
                System.out.println("Scheduled process PID: " + runningProcess.pid +
                        " (" + runningProcess.programName + ") PC: " + runningProcess.pc);
            } else {
                // Só usa NOP se não houver processos reais
                if (nopProcess != null) {
                    runningProcess = nopProcess;
                    runningProcess.state = ProcessState.RUNNING;
                    runningProcess.loadContext();
                    System.out.println("Scheduled NOP process (idle CPU).");
                } else {
                    runningProcess = null;
                    System.out.println("Nenhum processo disponível para escalonamento!");
                }
            }
        } finally {
            processLock.unlock();
        }
    }

    public PCB handleIOAndblockRunningProcess(int adress){
        try {
            processLock.lock();

            var savedProcess = runningProcess;
            runningProcess.IOReturnAddress = adress;
            runningProcess.state = ProcessState.BLOCKED;
            runningProcess.isWaitingIORequest = true;
            runningProcess.pc = hw.cpu.pc++;
            runningProcess.saveContext();

            blockedQueue.add(runningProcess);
            runningProcess = null;

            schedule();
            return savedProcess;
        } finally {
            processLock.unlock();
        }
    }

    // Handle - interupção de relógio
    public void handleTimerInterrupt() {
        try {
            processLock.lock();
            if (runningProcess != null) {
                System.out.println("Interrupção de relógio PID: " + runningProcess.pid +
                        " (" + runningProcess.programName + ") - troca de contexto");
            }
            schedule();
        } catch (Exception e) {
            System.err.println("Erro no handleTimerInterrupt: " + e.getMessage());
        } finally {
            processLock.unlock();
        }
    }

    // Ciclo principal do escalonador - chamado continuamente pela thread do escalonador
    public void schedulerCycle() {
        PCB currentProcess;
        processLock.lock();
        try {
            if (runningProcess == null) {
                schedule();
            }
            currentProcess = runningProcess;
        } finally {
            processLock.unlock();
        }

        // Fora do lock:
        if (currentProcess != null) {
            TimerInterrupt timer = new TimerInterrupt();
            timer.start();
            if (currentProcess.pid != -1) {
                System.out.println("Process PID running: " + currentProcess.pid +
                        " (" + currentProcess.programName + ")");
            }
            cpu.run();
            timer.stopTimer();
        }
    }


    // Teste se há processos que podem ser escalonados
    public boolean hasProcessesToSchedule() {
        try {
            processLock.lock();
            // Sempre retorna true se há NOP disponível, garantindo que o escalonador continue rodando
            return runningProcess != null || !readyQueue.isEmpty() || !blockedQueue.isEmpty() || nopProcess != null;
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
                String status = runningProcess.pid == -1 ? "IDLE" : runningProcess.state.toString();
                System.out.printf("%d\t%s\t%s\t\t%d%n",
                        runningProcess.pid,
                        status,
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
                System.out.println("Apenas processo NOP disponível (sistema idle).");
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

            // Mostra informações sobre processos na memória
            memoryManager.printMemoryStats();
        } finally {
            processLock.unlock();
        }
    }

    // Mata um processo específico pelo PID
    public boolean killProcess(int pid) {
        try {
            processLock.lock();

            // Não permite matar o processo NOP
            if (pid == -1) {
                System.out.println("Não é possível terminar o processo NOP do sistema.");
                return false;
            }

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
                memoryManager.deallocProcess(pid); // Usa PID para liberar memória
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
                memoryManager.deallocProcess(pid); // Usa PID para liberar memória
                System.out.println("Processo com PID " + pid + " removido da fila de bloqueados.");
                return true;
            }

            return false;
        } finally {
            processLock.unlock();
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
        for (PCB pcb : blockedQueue) {
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
                // Não termina o processo NOP, apenas escalona outro
                if (runningProcess.pid == -1) {
                    System.out.println("Processo NOP sendo substituído por escalonamento normal");
                    schedule();
                    return;
                }

                System.out.println("Process PID: " + runningProcess.pid + " terminated");

                // Libera memória usando PID
                memoryManager.deallocProcess(runningProcess.pid);

                runningProcess = null;

                // Escalona próximo processo (pode ser NOP se não houver outros)
                schedule();
            }
        } finally {
            processLock.unlock();
        }
    }

    // Verifica se tem algum processo ativo / admitido (excluindo NOP)
    public boolean hasActiveProcesses() {
        try {
            processLock.lock();
            // Considera apenas processos reais (PID != -1)
            boolean hasRunning = runningProcess != null && runningProcess.pid != -1;
            return hasRunning || !readyQueue.isEmpty() || !blockedQueue.isEmpty();
        } finally {
            processLock.unlock();
        }
    }

    /**
     * Força uma nova escalonação - útil quando novos processos são adicionados
     */
    public void forceReschedule() {
        try {
            processLock.lock();
            // Se está rodando NOP e há processos reais esperando
            if (runningProcess != null && runningProcess.pid == -1 && !readyQueue.isEmpty()) {
                cpu.setInterupt(Interrupts.intTimer);
            }
        } finally {
            processLock.unlock();
        }
    }

    private class TimerInterrupt extends Thread {
        private volatile boolean running;

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
                    if (runningProcess != null && runningProcess.pid == -1) {
                        Thread.sleep(1000);
                    } else {
                        Thread.sleep(10000);
                    }

                    if (running) {
                        cpu.setInterupt(Interrupts.intTimer);
                        break;
                    }
                }
            } catch (InterruptedException e) {
                // Thread foi interrompida, finaliza normalmente
            }
        }
    }
}