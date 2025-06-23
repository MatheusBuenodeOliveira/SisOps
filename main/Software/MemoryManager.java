package Software;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import Programs.Program;
import Hardware.*;

public class MemoryManager {
    ArrayList<Page> pageList = new ArrayList<>();
    Memory mem;
    Disk disk;

    // Mapeamento usando PID em vez de apenas nome do processo
    private Map<Integer, ArrayList<Page>> processVirtualPages = new HashMap<>();
    // Mapeamento para saber qual PID corresponde a qual programa
    private Map<Integer, String> pidToProcessName = new HashMap<>();

    // Para algoritmo de substituição FIFO simples
    private ArrayList<Page> memoryQueue = new ArrayList<>();

    // Estatísticas
    private int pageFaults = 0;
    private int pageSwaps = 0;

    public MemoryManager(Memory mem, Disk disk) {
        this.mem = mem;
        this.disk = disk;

        // Inicializa páginas físicas
        for(int i = 0; i < mem.pos.length; i += 8) {
            Page physicalPage = new Page(i, i + 8, 8, false);
            physicalPage.isInMemory = false;
            pageList.add(physicalPage);
        }
    }

    /**
     * Aloca páginas virtuais para um processo usando PID
     */
    public ArrayList<Page> alloc(Word[] program) {
        return alloc(program, "UnknownProcess", -1);
    }

    public ArrayList<Page> alloc(Word[] program, String processName) {
        return alloc(program, processName, -1);
    }

    public ArrayList<Page> alloc(Word[] program, String processName, int pid) {
        int programSize = program.length;
        int requiredPages = (int) Math.ceil((double) programSize / 8.0);
        ArrayList<Page> virtualPages = new ArrayList<>();

        System.out.println("Alocando " + requiredPages + " páginas virtuais para processo: " +
                processName + " (PID: " + pid + ")");
        System.out.println("Tamanho do programa: " + programSize + " palavras");

        // Registra o mapeamento PID -> nome do processo
        if (pid != -1) {
            pidToProcessName.put(pid, processName);
        }

        for (int pageNum = 0; pageNum < requiredPages; pageNum++) {
            Page virtualPage = new Page(-1, -1, 8, true);
            virtualPage.isInMemory = false;
            virtualPage.processName = processName;
            virtualPage.pid = pid; // Adiciona PID à página
            virtualPage.virtualPageNumber = pageNum;

            // Salva os dados da página no disco usando PID
            int startIndex = pageNum * 8;
            int endIndex = Math.min(startIndex + 8, programSize);
            Word[] pageData = new Word[8];

            for (int i = 0; i < 8; i++) {
                if (startIndex + i < programSize) {
                    pageData[i] = program[startIndex + i];
                } else {
                    pageData[i] = new Word(Opcode.___, -1, -1, -1);
                }
            }

            disk.storePage(pid, processName, pageNum, pageData);
            virtualPages.add(virtualPage);
        }

        // Carrega APENAS a primeira página na memória
        if (requiredPages > 0) {
            loadPageToMemory(virtualPages.get(0), pid, processName);
            System.out.println("Primeira página carregada na memória. Páginas restantes ficam no disco.");
        }

        processVirtualPages.put(pid, virtualPages);
        return virtualPages;
    }

    /**
     * Carrega uma página do disco para a memória física usando PID
     */
    public boolean loadPageToMemory(Page virtualPage, int pid, String processName) {
        // Procura uma página física livre
        Page physicalPage = findFreePhysicalPage();

        if (physicalPage == null) {
            // Não há páginas livres, precisa fazer swap
            physicalPage = selectPageForSwap();
            if (physicalPage == null) {
                System.out.println("Erro: Não foi possível encontrar página para swap!");
                return false;
            }
            swapOut(physicalPage);
        }

        // Carrega dados do disco usando PID
        Word[] pageData = disk.loadPage(pid, processName, virtualPage.virtualPageNumber);
        if (pageData == null) {
            System.out.println("Erro: Página não encontrada no disco!");
            return false;
        }

        // Copia dados para a memória física
        for (int i = 0; i < Math.min(pageData.length, physicalPage.size); i++) {
            mem.pos[physicalPage.pageStart + i] = pageData[i];
        }

        // Atualiza metadados da página virtual
        virtualPage.pageStart = physicalPage.pageStart;
        virtualPage.pageEnd = physicalPage.pageEnd;
        virtualPage.isInMemory = true;

        // Marca página física como ocupada
        physicalPage.inUse = true;
        physicalPage.processName = processName;
        physicalPage.pid = pid; // Adiciona PID à página física
        physicalPage.virtualPageNumber = virtualPage.virtualPageNumber;

        // Adiciona à fila de memória para FIFO
        memoryQueue.add(physicalPage);

        System.out.println("Página " + virtualPage.virtualPageNumber + " do processo " +
                processName + " (PID: " + pid + ") carregada na memória física (endereço " +
                physicalPage.pageStart + ")");

        return true;
    }

    /**
     * Encontra uma página física livre
     */
    private Page findFreePhysicalPage() {
        for (Page page : pageList) {
            if (!page.inUse) {
                return page;
            }
        }
        return null;
    }

    /**
     * Seleciona uma página para fazer swap usando FIFO
     */
    private Page selectPageForSwap() {
        if (memoryQueue.isEmpty()) {
            return null;
        }
        return memoryQueue.remove(0);
    }

    /**
     * Faz swap out de uma página (salva no disco e libera da memória)
     */
    private void swapOut(Page physicalPage) {
        if (physicalPage.processName == null || physicalPage.pid == -1) {
            return;
        }

        // Salva dados atuais no disco
        Word[] pageData = new Word[physicalPage.size];
        for (int i = 0; i < physicalPage.size; i++) {
            pageData[i] = mem.pos[physicalPage.pageStart + i];
        }

        disk.storePage(physicalPage.pid, physicalPage.processName,
                physicalPage.virtualPageNumber, pageData);

        // Encontra a página virtual correspondente e atualiza
        ArrayList<Page> processPages = processVirtualPages.get(physicalPage.pid);
        if (processPages != null) {
            for (Page virtualPage : processPages) {
                if (virtualPage.virtualPageNumber == physicalPage.virtualPageNumber) {
                    virtualPage.isInMemory = false;
                    virtualPage.pageStart = -1;
                    virtualPage.pageEnd = -1;
                    break;
                }
            }
        }

        System.out.println("Página swapped out: processo " + physicalPage.processName +
                " (PID: " + physicalPage.pid + "), página " + physicalPage.virtualPageNumber);

        // Libera página física
        physicalPage.inUse = false;
        physicalPage.processName = null;
        physicalPage.pid = -1;
        physicalPage.virtualPageNumber = -1;

        pageSwaps++;
    }

    /**
     * Trata page fault usando PID
     */
    public boolean handlePageFault(int pid, String processName, int virtualPageNumber) {
        pageFaults++;
        System.out.println("PAGE FAULT: Processo " + processName + " (PID: " + pid +
                "), página " + virtualPageNumber);


        ArrayList<Page> processPages = processVirtualPages.get(pid);
        if (processPages == null || virtualPageNumber >= processPages.size()) {
            System.out.println("Erro: Página virtual inválida!");
            return false;
        }

        Page virtualPage = processPages.get(virtualPageNumber);
        if (virtualPage.isInMemory) {
            System.out.println("Aviso: Página já está na memória!");
            return true;
        }

        // Carrega a página na memória
        return loadPageToMemory(virtualPage, pid, processName);
    }

    /**
     * Libera todas as páginas de um processo usando PID
     */
    public void deallocProcess(int pid) {
        ArrayList<Page> processPages = processVirtualPages.get(pid);
        if (processPages == null) {
            return;
        }

        String processName = pidToProcessName.get(pid);

        // Libera páginas que estão na memória física
        for (Page virtualPage : processPages) {
            if (virtualPage.isInMemory) {
                // Encontra e libera a página física correspondente
                for (Page physicalPage : pageList) {
                    if (physicalPage.inUse &&
                            physicalPage.pid == pid &&
                            physicalPage.virtualPageNumber == virtualPage.virtualPageNumber) {

                        physicalPage.inUse = false;
                        physicalPage.processName = null;
                        physicalPage.pid = -1;
                        physicalPage.virtualPageNumber = -1;
                        memoryQueue.remove(physicalPage);
                        break;
                    }
                }
            }
        }

        // Remove dados do disco
        disk.removeProcess(pid);

        // Remove dos mapeamentos
        processVirtualPages.remove(pid);
        pidToProcessName.remove(pid);

        System.out.println("Processo " + processName + " (PID: " + pid +
                ") desalocado da memória virtual");
    }

    /**
     * Verifica se uma página virtual está na memória usando PID
     */
    public boolean isPageInMemory(int pid, int virtualPageNumber) {
        ArrayList<Page> processPages = processVirtualPages.get(pid);
        if (processPages == null || virtualPageNumber >= processPages.size()) {
            return false;
        }
        return processPages.get(virtualPageNumber).isInMemory;
    }

    /**
     * Obtém endereço físico de uma página virtual usando PID
     */
    public int getPhysicalAddress(int pid, int virtualPageNumber, int offset) {
        ArrayList<Page> processPages = processVirtualPages.get(pid);
        if (processPages == null || virtualPageNumber >= processPages.size()) {
            return -1;
        }

        Page virtualPage = processPages.get(virtualPageNumber);
        if (!virtualPage.isInMemory) {
            return -1; // Vai gerar page fault
        }

        return virtualPage.pageStart + offset;
    }

    /**
     * Estatísticas do sistema de memória virtual
     */
    public void printMemoryStats() {
        System.out.println("=== ESTATÍSTICAS DE MEMÓRIA VIRTUAL ===");
        System.out.println("Page Faults: " + pageFaults);
        System.out.println("Page Swaps: " + pageSwaps);

        int totalPhysicalPages = pageList.size();
        int usedPhysicalPages = 0;
        for (Page page : pageList) {
            if (page.inUse) usedPhysicalPages++;
        }

        System.out.println("Páginas físicas total: " + totalPhysicalPages);
        System.out.println("Páginas físicas em uso: " + usedPhysicalPages);
        System.out.println("Páginas físicas livres: " + (totalPhysicalPages - usedPhysicalPages));

        System.out.println("Processos na memória virtual: " + processVirtualPages.size());

        for (Integer pid : processVirtualPages.keySet()) {
            String processName = pidToProcessName.get(pid);
            ArrayList<Page> pages = processVirtualPages.get(pid);
            int pagesInMemory = 0;
            for (Page page : pages) {
                if (page.isInMemory) pagesInMemory++;
            }
            System.out.println("  " + processName + " (PID: " + pid + "): " +
                    pagesInMemory + "/" + pages.size() + " páginas na memória");
        }
        System.out.println("========================================");
    }
}