package Software;

import Hardware.*;

public class SchedulerThread extends Thread {
    private final ProcessManager processManager;
    private volatile boolean running = true;
    private static final int SCHEDULER_SLEEP_MS = 5; // Tempo de espera entre verificações

    public SchedulerThread(ProcessManager processManager) {
        this.processManager = processManager;
        this.setName("Scheduler-Thread");
    }

    @Override
    public void run() {
        System.out.println("Iniciando thread de escalonamento...");

        while (running) {
            try {
                // Se houver processos para executar, o escalonador os gerencia
                if (processManager.hasProcessesToSchedule()) {
                    processManager.schedulerCycle();
                } else {
                    Thread.sleep(SCHEDULER_SLEEP_MS);
                }
            } catch (Exception e) {
                System.err.println("Erro na thread do escalonador: " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("Thread de escalonamento finalizada.");
    }

    public void stopScheduler() {
        this.running = false;
        this.interrupt();
    }
}