package hudson.model;

public class GitlabCause extends Cause {

    public final String buildId;
    public final String md5;

    public GitlabCause(String buildId, String md5) {
        this.buildId = buildId;
        this.md5 = md5;
    }

    @Override
    public String getShortDescription() {
        return "Triggered by Gitlab";
    }
}
