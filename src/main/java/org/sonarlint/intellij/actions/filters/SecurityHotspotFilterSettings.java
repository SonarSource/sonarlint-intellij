package org.sonarlint.intellij.actions.filters;

public class SecurityHotspotFilterSettings {

  private SecurityHotspotFilters currentlySelectedFilter;

  public SecurityHotspotFilterSettings() {
    currentlySelectedFilter = SecurityHotspotFilters.SHOW_ALL;
  }

  public SecurityHotspotFilters getCurrentlySelectedFilter() {
    return currentlySelectedFilter;
  }

  public void setCurrentlySelectedFilter(SecurityHotspotFilters filter) {
    currentlySelectedFilter = filter;
  }

}
