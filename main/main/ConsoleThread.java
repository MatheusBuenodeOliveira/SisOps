package main;

import java.util.Scanner;
import Programs.Programs;
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
                var parts = command.split(" ");
                if (parts.length < 2) {
                    System.out.println("Uso: dump <id_do_processo>");
                    return;
                }
                dumpProcess(Integer.parseInt(parts[1]));
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

    private void dumpProcess(int processId) {
        ProcessManager.PCB pcb = processManager.getProcess(processId);
        if (pcb == null) {
            System.out.println("Processo " + processId + " não encontrado");
            return;
        }

        System.out.println("=== Dump do Processo " + processId + " ===");
        System.out.println("Estado: " + pcb.state);
        System.out.println("PC: " + pcb.pc);

        System.out.println("Registradores:");
        for (int i = 0; i < pcb.registers.length; i++) {
            System.out.println("R" + i + ": " + pcb.registers[i]);
        }

        System.out.println("Páginas:");
        for (Page page : pcb.pages) {
            System.out.println("  Início: " + page.pageStart + ", Fim: " + page.pageEnd);
            // Dump do conteúdo da memória para cada página
            sistema.so.utils.dump(page.pageStart, page.pageEnd);
        }
    }

    private void executeProgram(String command) {
        String[] parts = command.split("\\s+");
        if (parts.length != 2) {
            System.out.println("Uso: exec [nome_do_programa]");
            return;
        }

        String programName = parts[1];
        var program = programs.retrieveProgram(programName);

        if (program == null) {
            System.out.println("Programa não encontrado: " + programName);
            return;
        }

        var pcb = processManager.createProcess(program);
        if (pcb != null) {
            System.out.println("Processo criado com PID: " + pcb.pid + " para o programa: " + programName);
        } else {
            System.out.println("Falha ao criar processo para o programa: " + programName);
        }
    }

    private void createProgram(String nome){
        var newProgram = new Programs().retrieveProgram(nome);
        if (newProgram == null) {
            System.out.println("=== Programa não reconhecido pelo sistema ===");
            return;
        }
        System.out.println("=== Criando Processo ===");
        processManager.createProcess(newProgram);
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
