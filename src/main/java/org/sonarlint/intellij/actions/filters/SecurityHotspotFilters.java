package org.sonarlint.intellij.actions.filters;

public enum SecurityHotspotFilters {

  SHOW_ALL("Show all"),
  LOCAL_ONLY("Local only"),
  MATCHED_ON_SONARQUBE("Matched on SonarQube");

  private final String title;

  SecurityHotspotFilters(String title) {
    this.title = title;
  }

  public String getTitle() {
    return title;
  }

}
