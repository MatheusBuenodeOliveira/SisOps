package Software;

public class Page {
    public int pageStart;
    public int pageEnd;
    public int size;
    public boolean inUse;
    public boolean isInMemory = false;  // Para memória virtual
    public String processName;          // Nome do programa
    public int pid = -1;                // PID do processo (NOVO)
    public int virtualPageNumber = -1;  // Número da página virtual

    public Page(int pageStart, int pageEnd, int size, boolean inUse) {
        this.pageStart = pageStart;
        this.pageEnd = pageEnd;
        this.size = size;
        this.inUse = inUse;
    }

    @Override
    public String toString() {
        return "Page{" +
                "start=" + pageStart +
                ", end=" + pageEnd +
                ", size=" + size +
                ", inUse=" + inUse +
                ", inMemory=" + isInMemory +
                ", process='" + processName + '\'' +
                ", pid=" + pid +
                ", virtualPageNum=" + virtualPageNumber +
                '}';
    }
}