public class MultiQuickFixableFile {

  public MultiQuickFixableFile() {
    method(new HashMap<String,<caret> Integer>(), new HashMap<String, Integer>());
  }

  public void method(Map<String, Integer> map1, Map<String, Integer> map2) {
    // not implemented
  }
}
