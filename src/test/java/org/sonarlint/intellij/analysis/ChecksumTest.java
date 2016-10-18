package org.sonarlint.intellij.analysis;

import java.security.MessageDigest;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.StringUtils;
import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ChecksumTest {
  private final MessageDigest md5Digest = DigestUtils.getMd5Digest();

  @Test
  public void test_() {
    System.out.println(checksumByServer("  Cli parse(String[] args) {"));
    System.out.println(checksumByServer("i++;"));
  }

  String checksumByServer(String line) {
    String reducedLine = StringUtils.replaceChars(line, "\t ", "");
    if (reducedLine.isEmpty()) {
      return "";
    }
    return Hex.encodeHexString(md5Digest.digest(line.getBytes(UTF_8)));
  }
}
