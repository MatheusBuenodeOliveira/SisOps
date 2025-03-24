package Software;

public class Page {
    public int pageStart;
    public int pageEnd;
    public int size;
    public boolean inUse;

    public Page(int pages, int pagee, int size, boolean use){
        this.pageStart = pages;
        this.pageEnd = pagee;
        this.size = size;
        this.inUse = use;
    }
}
