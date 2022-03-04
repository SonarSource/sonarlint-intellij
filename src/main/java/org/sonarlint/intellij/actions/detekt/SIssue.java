package org.sonarlint.intellij.actions.detekt;

public class SIssue {
    private String issue_id;
    private String name;
    private String des;

    private SIssue(SIssueBuilder sIssueBuilder) {
        issue_id = sIssueBuilder.issue_id;
        name = sIssueBuilder.name;
        des = sIssueBuilder.des;
    }

    public String getIssue_id() {
        return issue_id;
    }

    public String getName() {
        return name;
    }

    public String getDes() {
        return des;
    }

    public void setDes(String des) {
        this.des = des;
    }

    public static class SIssueBuilder {
        private String issue_id;
        private String name;
        private String des;

        public SIssueBuilder name(String name) {
            this.name = name;
            return this;
        }

        public SIssueBuilder des(String des) {
            this.des = des;
            return this;
        }

        public SIssueBuilder issueId(String issueId) {
            this.issue_id = issueId;
            return this;
        }

        public SIssue build() {
            return new SIssue(this);
        }

    }
}
