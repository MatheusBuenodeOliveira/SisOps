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
            cpu.pc = this.pc;
            for (int i = 0; i < this.registers.length; i++) {
                cpu.reg[i] = this.registers[i];
            }
            cpu.setContext(this.pages);
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

    // Create a new process from a program
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

    // Schedule the next process (Round Robin)
    public void schedule() {
        if (runningProcess != null) {
            // Save current process context
            runningProcess.saveContext();

            if (runningProcess.state == ProcessState.RUNNING) {
                runningProcess.state = ProcessState.READY;
                readyQueue.add(runningProcess);
            }
            // If the process is BLOCKED, it's already in blockedQueue
        }

        // Get next process from queue
        if (!readyQueue.isEmpty()) {
            runningProcess = readyQueue.poll();
            runningProcess.state = ProcessState.RUNNING;
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
            request.completionTime = currentTime + 5; // Assume I/O takes 5 time units

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

    // Check for completed I/O operations
    public void checkIOCompletion() {
        int currentTime = getCurrentTime();
        Queue<PCB> stillBlocked = new LinkedList<>();

        while (!blockedQueue.isEmpty()) {
            PCB process = blockedQueue.poll();

            if (process.pendingIO.completionTime <= currentTime) {
                // I/O completed
                System.out.println("I/O completed for PID: " + process.pid);
                process.state = ProcessState.READY;
                process.pendingIO = null;
                readyQueue.add(process);
            } else {
                // I/O not yet completed
                stillBlocked.add(process);
            }
        }

        // Update blocked queue with processes still waiting
        blockedQueue = stillBlocked;
    }

    // Handle I/O syscalls
    public void handleIO(int syscallType, int address, int value) {
        if (runningProcess == null) return;

        if (syscallType == 1) { // Read operation
            IORequest request = new IORequest(IORequest.IOType.READ, address);
            blockForIO(request);
        } else if (syscallType == 2) { // Write operation
            IORequest request = new IORequest(IORequest.IOType.WRITE, address, value);
            blockForIO(request);
        }
    }

    // Handle timer interrupt - called from InterruptHandling
    public void handleTimerInterrupt() {
        System.out.println("Timer interrupt "+runningProcess.pid+"- context switch");
        schedule();
    }

    // Run the system
    public void run() {
        // Start with the first process
        if (runningProcess == null && !readyQueue.isEmpty()) {
            schedule();
        }

        // Run the current process until interrupted or terminated
        while (hasActiveProcesses()) {
            tickClock();
            checkIOCompletion();

            if (runningProcess != null) {
                // Run the process for TIME_SLICE instructions
                TimerInterrupt timer = new TimerInterrupt(this, TIME_SLICE);
                timer.start();

                // Let the CPU run until interrupted or process terminates
                System.out.println("Process PID running: " + runningProcess.pid);
                cpu.run();

                timer.stopTimer();

                // If process terminated on its own, schedule next process
                if (runningProcess != null && runningProcess.state == ProcessState.RUNNING) {
                    // Process didn't terminate but was interrupted
                    // The timer or I/O interrupt will have already scheduled the next process
                }
            } else if (!readyQueue.isEmpty()) {
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

    // Check if there are any active processes
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
                    // Simple way to estimate executed instructions by watching PC
                    int currentPC = cpu.pc;
                    if (currentPC != startPC) {
                        instructionsExecuted++;
                        startPC = currentPC;

                        // Check if time slice is over
                        if (instructionsExecuted >= instructions) {
                            // Generate timer interrupt
                            interruptHandler.handle(Interrupts.intTimer);
                            break;
                        }
                    }

                    // Small delay to avoid high CPU usage
                    Thread.sleep(1);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}