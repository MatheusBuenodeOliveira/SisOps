package main;

import java.util.Scanner;
import Programs.Programs;
import Software.Interrupts;
import Software.Page;
import Software.ProcessManager;
import main.Sistema;

public class ConsoleThread extends Thread {
    private final Sistema sistema;
    private final Programs programs;
    private final ProcessManager processManager;
    private boolean running = true;
    private final Scanner scanner = new Scanner(System.in);

    public ConsoleThread(Sistema sistema, Programs programs, ProcessManager processManager) {
        this.sistema = sistema;
        this.programs = programs;
        this.processManager = processManager;
    }

    @Override
    public void run() {
        printHelp();
        while (running) {
            System.out.print("SisOps> ");
            String command = scanner.nextLine().trim();
            String[] tokens = command.split(" ");

            if (command.equals("exit")) {
                exit();
            } else if (command.equals("help")) {
                printHelp();
            } else if (command.startsWith("list")) {
                listPrograms();
            } else if (command.startsWith("ps")) {
                listProcesses();
            } else if (command.startsWith("dump")) {
                dumpProcess();
            } else if (command.startsWith("exec")) {
                executeProgram(command);
            } else if (command.equals("mem")) {
                showMemory();
            } else if (command.startsWith("kill")) {
                killProcess(command);
            } else if(tokens[0].equals("new")){
                createProgram(tokens[1]);
            } else if (command.equals("hacf")) {
                processManager.startSchedulerThread();
            } else if (command.equals("schkill")) {
                processManager.shutdownScheduler();
            } else if (command.startsWith("IO")) {
                int pid = Integer.parseInt(tokens[1]);
                int value = Integer.parseInt(tokens[2]);

                ProcessManager.PCB found = null;
                for (ProcessManager.PCB p : processManager.blockedQueue) {
                    if (p.pid == pid) {
                        found = p;
                        break;
                    }
                }

                if (found != null) {
                    found.IOReturnValue = value;
                    found.PendingPageUpdate = true;
                    sistema.hw.cpu.ReturningOfIO.add(found);
                    sistema.hw.cpu.setInterupt(Interrupts.IOReturn);
                    System.out.println("Valor de IO retornado para o processo " + pid + ": " + value);
                } else {
                    System.out.println("Processo com PID " + pid + " não encontrado na fila de bloqueados.");
                }

            } else {
                System.out.println("Comando desconhecido. Digite 'help' para ver os comandos disponíveis.");
            }
        }
    }

    private void printHelp() {
        System.out.println("=== SisOps - Sistema Operacional Simulado ===");
        System.out.println("Comandos disponíveis:");
        System.out.println("  help         - Mostra esta ajuda");
        System.out.println("  list         - Lista programas disponíveis");
        System.out.println("  dump [pid]   - Faz o dump de um processo especificado");
        System.out.println("  exec [prog]  - Executa um programa");
        System.out.println("  ps           - Lista processos em execução");
        System.out.println("  mem          - Mostra estado da memória");
        System.out.println("  kill [pid]   - Termina um processo");
        System.out.println("  new <p>      - Cria um novo processo");
        System.out.println("  hacf         - Que os jogos começem");
        System.out.println("  schkill      - Derruba a thread de escalonamento ");
        System.out.println("  IO [pid] [value]   - Retorna um valor de IO para o processo ");
        System.out.println("  exit         - Sai do sistema");
        System.out.println("=========================================");
    }

    private void listPrograms() {
        System.out.println("Programas disponíveis:");
        for (int i = 0; i < programs.progs.length; i++) {
            if (programs.progs[i] != null) {
                System.out.println("  " + programs.progs[i].name);
            }
        }
    }

    private void dumpProcess() {
        sistema.so.utils.dump(0,sistema.hw.mem.pos.length -1);
    }

    private void executeProgram(String command) {
        String[] parts = command.split("\\s+");
        if (parts.length != 2) {
            System.out.println("Uso: exec <nome_do_programa>");
            System.out.println("Digite 'list' para ver os programas disponíveis.");
            return;
        }

        String programName = parts[1];
        var program = programs.retrieveProgram(programName);

        if (program == null) {
            System.out.println("Programa não encontrado: " + programName);
            System.out.println("Digite 'list' para ver os programas disponíveis.");
            return;
        }

        var pcb = processManager.createProcess(program);
        if (pcb != null) {
            System.out.println("✓ Processo criado com sucesso!");
            System.out.println("  PID: " + pcb.pid);
            System.out.println("  Programa: " + programName);
            System.out.println("  Estado: " + pcb.state);

            // Força reescalonamento se necessário
            processManager.forceReschedule();
        } else {
            System.out.println("✗ Falha ao criar processo para o programa: " + programName);
        }
    }

    private void createProgram(String nome) {
        if (nome == null || nome.trim().isEmpty()) {
            System.out.println("Uso: new <nome_do_programa>");
            System.out.println("Digite 'list' para ver os programas disponíveis.");
            return;
        }

        var newProgram = new Programs().retrieveProgram(nome);
        if (newProgram == null) {
            System.out.println("✗ Programa '" + nome + "' não reconhecido pelo sistema");
            System.out.println("Digite 'list' para ver os programas disponíveis.");
            return;
        }

        System.out.println("=== Criando Novo Processo ===");
        var pcb = processManager.createProcess(newProgram);
        if (pcb != null) {
            System.out.println("✓ Processo criado com sucesso!");
            System.out.println("  PID: " + pcb.pid);
            System.out.println("  Programa: " + nome);

            // Força reescalonamento se necessário
            processManager.forceReschedule();
        } else {
            System.out.println("✗ Falha ao criar o processo");
        }
        System.out.println("=============================");
    }

    private void listProcesses() {
        System.out.println("Processos em execução:");
        processManager.listProcesses();
    }

    private void showMemory() {
        System.out.println("Estado da memória:");
        processManager.showMemoryStatus();
    }

    private void killProcess(String command) {
        String[] parts = command.split(" ");
        if (parts.length != 2) {
            System.out.println("Uso: kill [pid]");
            return;
        }

        try {
            int pid = Integer.parseInt(parts[1]);
            boolean success = processManager.killProcess(pid);
            if (success) {
                System.out.println("Processo com PID " + pid + " terminado com sucesso.");
            } else {
                System.out.println("Não foi possível terminar o processo com PID " + pid);
            }
        } catch (NumberFormatException e) {
            System.out.println("PID inválido. Use um número inteiro.");
        }
    }

    public void exit() {
        System.out.println("Saindo do sistema...");
        running = false;
        processManager.shutdownScheduler();
        System.exit(0);
    }
}
