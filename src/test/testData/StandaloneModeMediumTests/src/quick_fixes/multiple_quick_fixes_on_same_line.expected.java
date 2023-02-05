public class MultiQuickFixableFile {

  public MultiQuickFixableFile() {
    method(new HashMap<>(), new HashMap<<caret>>());
  }

  public void method(Map<String, Integer> map1, Map<String, Integer> map2) {
    // not implemented
  }
}
