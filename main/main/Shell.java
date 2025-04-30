package main;

import java.util.Scanner;
import java.util.ArrayList;

import Hardware.*;
import Programs.*;
import Software.Page;
import Software.ProcessManager;
import Software.SO;
import main.Sistema;

public class Shell {
    private Sistema sistema;
    private Programs progs;
    private ProcessManager processManager;
    private SO so;
    private HW hw;
    private boolean running = true;
    private boolean trace = false;

    public Shell(Sistema sistema) {
        this.sistema = sistema;
        this.progs = sistema.progs;
        this.so = sistema.so;
        this.hw = sistema.hw;
        this.processManager = so.processManager;
    }

    public void run() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== Sistema de Simulação de CPU ===");
        System.out.println("Digite 'help' para ver os comandos disponíveis");

        while (running) {
            System.out.print("> ");
            String command = scanner.nextLine().trim();
            processCommand(command);
        }
        scanner.close();
    }

    private void processCommand(String input) {
        String[] parts = input.split("\\s+");
        String command = parts[0].toLowerCase();

        try {
            switch (command) {
                case "new":
                    if (parts.length < 2) {
                        System.out.println("Uso: new <nome_do_programa>");
                        return;
                    }
                    createProcess(parts[1]);
                    break;

                case "rm":
                    if (parts.length < 2) {
                        System.out.println("Uso: rm <id_do_processo>");
                        return;
                    }
                    removeProcess(Integer.parseInt(parts[1]));
                    break;

                case "ps":
                    listProcesses();
                    break;

                case "dump":
                    if (parts.length < 2) {
                        System.out.println("Uso: dump <id_do_processo>");
                        return;
                    }
                    dumpProcess(Integer.parseInt(parts[1]));
                    break;

                case "dumpm":
                    if (parts.length < 3) {
                        System.out.println("Uso: dumpM <início> <fim>");
                        return;
                    }
                    dumpMemory(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                    break;

                case "exec":
                    if (parts.length < 2) {
                        System.out.println("Uso: exec <id_do_processo>");
                        return;
                    }
                    executeProcess(Integer.parseInt(parts[1]));
                    break;

                case "mget":
                    if (parts.length < 2 || !parts[1].equalsIgnoreCase("all")) {
                        System.out.println("Uso: mget all");
                        return;
                    }
                    runAllProcesses();
                    break;

                case "exit":
                    running = false;
                    System.out.println("Saindo do sistema...");
                    break;

                case "help":
                    showHelp();
                    break;

                default:
                    System.out.println("Comando desconhecido: " + command);
                    System.out.println("Digite 'help' para ver os comandos disponíveis");
            }
        } catch (NumberFormatException e) {
            System.out.println("Erro: parâmetro numérico inválido");
        } catch (Exception e) {
            System.out.println("Erro ao executar o comando: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createProcess(String programName) {
        Program program = progs.retrieveProgram(programName);
        if (program == null) {
            System.out.println("Programa não encontrado: " + programName);
            return;
        }

        ProcessManager.PCB pcb = processManager.createProcess(program);
        if (pcb != null) {
            System.out.println("Processo criado com ID: " + pcb.pid + " para o programa: " + programName);
        } else {
            System.out.println("Falha ao criar o processo para o programa: " + programName);
        }
    }

    private void removeProcess(int processId) {
        boolean removed = processManager.removeProcess(processId);
        if (removed) {
            System.out.println("Processo " + processId + " removido com sucesso");
        } else {
            System.out.println("Processo " + processId + " não encontrado");
        }
    }

    private void listProcesses() {
        System.out.println("=== Lista de Processos ===");
        ArrayList<ProcessManager.PCB> processes = processManager.getAllProcesses();

        if (processes.isEmpty()) {
            System.out.println("Nenhum processo no sistema");
            return;
        }

        System.out.println("PID\tEstado\t\tPC\tPáginas");
        for (ProcessManager.PCB pcb : processes) {
            System.out.printf("%d\t%s\t\t%d\t%d\n",
                    pcb.pid,
                    pcb.state,
                    pcb.pc,
                    pcb.pages.size());
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
            so.utils.dump(page.pageStart, page.pageEnd);
        }
    }

    private void dumpMemory(int start, int end) {
        if (start < 0 || end >= hw.mem.pos.length || start > end) {
            System.out.println("Endereços inválidos. Range válido: 0-" + (hw.mem.pos.length - 1));
            return;
        }

        System.out.println("=== Dump da Memória [" + start + "-" + end + "] ===");
        so.utils.dump(start, end + 1);
    }

    private void executeProcess(int processId) {
        ProcessManager.PCB pcb = processManager.getProcess(processId);
        if (pcb == null) {
            System.out.println("Processo " + processId + " não encontrado");
            return;
        }

        System.out.println("Executando processo " + processId);
        processManager.executeProcess(processId);
    }

    private void runAllProcesses() {
        System.out.println("Executando todos os processos com Round Robin scheduler");
        processManager.run();
        System.out.println("Todos os processos foram concluídos");
    }

    private void showHelp() {
        System.out.println("=== Comandos Disponíveis ===");
        System.out.println("new <nome_do_programa> - Cria um novo processo");
        System.out.println("rm <id> - Remove o processo com o ID especificado");
        System.out.println("ps - Lista todos os processos existentes");
        System.out.println("dump <id> - Mostra o PCB e memória do processo com o ID especificado");
        System.out.println("dumpM <início> <fim> - Mostra o conteúdo da memória no intervalo especificado");
        System.out.println("exec <id> - Executa o processo com o ID especificado");
        System.out.println("mget all - Executa todos os processos usando o escalonador Round Robin");
        System.out.println("exit - Sai do sistema");
        System.out.println("help - Mostra esta mensagem de ajuda");
    }
}