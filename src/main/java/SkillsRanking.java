public class SkillsRanking {
    private String type;
    private int rank;
    private int score;
    private VexEventInfo event;
    private VexSeasonInfo season;

    public SkillsRanking(String type, int rank, int score, VexEventInfo event, VexSeasonInfo season) {
        this.type = type;
        this.rank = rank;
        this.score = score;
        this.event = event;
        this.season = season;
    }

    public String getType() {
        return type;
    }

    public int getRank() {
        return rank;
    }

    public int getScore() {
        return score;
    }

    public VexEventInfo getEvent() {
        return event;
    }

    public VexSeasonInfo getSeason() {
        return season;
    }
}