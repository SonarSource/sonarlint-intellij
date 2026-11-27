import java.io.File;
import java.util.Arrays;

public class QuickFixableFile {
  void m() {
    var file = new File("");
    var array = new int[5];
    if (file != null) {
      if (file.isFile() || array.to<caret>String().equals("")) {
        /* ... */
      }
    }
  }
}