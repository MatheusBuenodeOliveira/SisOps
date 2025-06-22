package Hardware;

import java.util.HashMap;
import java.util.Map;
import Programs.Program;
import Programs.Programs;
import Software.Opcode;

public class Disk {

    // Armazenamento: PID -> (número da página -> dados)
    private Map<Integer, Map<Integer, Word[]>> diskStorage = new HashMap<>();
    // Mapeamento adicional para saber qual programa cada PID representa (para preload)
    private Map<String, Word[]> programTemplates = new HashMap<>();

    Disk(){
        Programs programs = new Programs();
        preloadProgramsToDisk(programs);
    }

    /**
     * Armazena uma página no disco usando PID
     */
    public void storePage(int pid, String processName, int pageNumber, Word[] pageData) {
        diskStorage.putIfAbsent(pid, new HashMap<>());
        diskStorage.get(pid).put(pageNumber, pageData);
    }

    /**
     * Carrega uma página do disco usando PID
     */
    public Word[] loadPage(int pid, String processName, int pageNumber) {
        if (diskStorage.containsKey(pid)) {
            return diskStorage.get(pid).get(pageNumber);
        }
        return null;
    }

    /**
     * Remove um processo do disco usando PID
     */
    public void removeProcess(int pid) {
        diskStorage.remove(pid);
    }

    /**
     * Verifica se uma página existe no disco para um PID específico
     */
    public boolean contains(int pid, int pageNumber) {
        return diskStorage.containsKey(pid) &&
                diskStorage.get(pid).containsKey(pageNumber);
    }

    /**
     * Cria uma cópia dos dados de um programa para um novo processo (PID específico)
     * Usado quando um novo processo é criado baseado em um programa existente
     */
    public void createProcessFromTemplate(int pid, String programName) {
        Word[] template = programTemplates.get(programName);
        if (template == null) {
            System.out.println("Erro: Template do programa " + programName + " não encontrado!");
            return;
        }

        int totalPages = (int) Math.ceil(template.length / 8.0);
        int wordIndex = 0;

        for (int page = 0; page < totalPages; page++) {
            int size = Math.min(8, template.length - wordIndex);
            Word[] pageData = new Word[8]; // Sempre 8 para manter consistência

            for (int i = 0; i < 8; i++) {
                if (i < size) {
                    pageData[i] = template[wordIndex++];
                } else {
                    pageData[i] = new Word(Opcode.___, -1, -1, -1); // Padding
                }
            }

            storePage(pid, programName, page, pageData);
        }

        System.out.println("Processo PID " + pid + " (" + programName + ") criado no disco com " +
                totalPages + " páginas");
    }

    /**
     * Pré-carrega programas no disco como templates
     * Estes templates serão copiados quando novos processos forem criados
     */
    public void preloadProgramsToDisk(Programs programs) {
        for (Program prog : programs.progs) {
            if (prog != null) {
                // Armazena como template para futuras cópias
                programTemplates.put(prog.name, prog.image);
                System.out.println("Template do programa " + prog.name + " carregado no disco");
            }
        }
    }

    /**
     * Lista todos os processos no disco (para debug)
     */
    public void listProcessesOnDisk() {
        System.out.println("=== PROCESSOS NO DISCO ===");
        for (Integer pid : diskStorage.keySet()) {
            int pageCount = diskStorage.get(pid).size();
            System.out.println("PID " + pid + ": " + pageCount + " páginas");
        }
        System.out.println("Templates disponíveis: " + programTemplates.keySet());
        System.out.println("========================");
    }

    /**
     * Obtém informações sobre um processo específico no disco
     */
    public void getProcessInfo(int pid) {
        if (diskStorage.containsKey(pid)) {
            Map<Integer, Word[]> pages = diskStorage.get(pid);
            System.out.println("Processo PID " + pid + " tem " + pages.size() + " páginas no disco");
            for (Integer pageNum : pages.keySet()) {
                System.out.println("  Página " + pageNum + " disponível");
            }
        } else {
            System.out.println("Processo PID " + pid + " não encontrado no disco");
        }
    }
}