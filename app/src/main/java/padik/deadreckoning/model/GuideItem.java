package padik.deadreckoning.model;

public class GuideItem {
    private String title;
    private String content;
    private int iconRes;

    public GuideItem(String title, String content, int iconRes) {
        this.title = title;
        this.content = content;
        this.iconRes = iconRes;
    }

    public String getTitle() { return title; }
    public String getContent() { return content; }
    public int getIconRes() { return iconRes; }
}
