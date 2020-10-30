package org.sonarlint.intellij.issue.hotspot;

import java.util.Arrays;
import java.util.Optional;

public enum SecurityHotspotCategory {
  BUFFER_OVERFLOW("buffer-overflow", "Buffer Overflow"),
  SQL_INJECTION("sql-injection", "SQL Injection"),
  RCE("rce", "Code Injection (RCE)"),
  OBJECT_INJECTION("object-injection", "Object Injection"),
  COMMAND_INJECTION("command-injection", "Command Injection"),
  PATH_TRAVERSAL_INJECTION("path-traversal-injection", "Path Traversal Injection"),
  LDAP_INJECTION("ldap-injection", "LDAP Injection"),
  XPATH_INJECTION("xpath-injection", "XPath Injection"),
  EXPRESSION_LANG_INJECTION("expression-lang-injection", "Expression Language Injection"),
  LOG_INJECTION("log-injection", "Log Injection"),
  XXE("xxe", "XML External Entity (XXE)"),
  XSS("xss", "Cross-Site Scripting (XSS)"),
  DOS("dos", "Denial of Service (DoS)"),
  SSRF("ssrf", "Server-Side Request Forgery (SSRF)"),
  CSRF("csrf", "Cross-Site Request Forgery (CSRF)"),
  HTTP_RESPONSE_SPLITTING("http-response-splitting", "HTTP Response Splitting"),
  OPEN_REDIRECT("open-redirect", "Open Redirect"),
  WEAK_CRYPTOGRAPHY("weak-cryptography", "Weak Cryptography"),
  AUTH("auth", "Authentication"),
  INSECURE_CONF("insecure-conf", "Insecure Configuration"),
  FILE_MANIPULATION("file-manipulation", "File Manipulation"),
  OTHERS("others", "Others");

  private final String shortName;
  private final String longName;

  SecurityHotspotCategory(String shortName, String longName) {
    this.shortName = shortName;
    this.longName = longName;
  }

  public String getLongName() {
    return longName;
  }

  public static Optional<SecurityHotspotCategory> findByShortName(String shortName) {
    return Arrays.stream(SecurityHotspotCategory.values())
      .filter(securityHotspotCategory -> securityHotspotCategory.shortName.equals(shortName))
      .findFirst();
  }
}
