import java.io.File;
import java.util.Arrays;

public class QuickFixableFile {
  void m() {
    var file = new File("");
    var array = new int[5];
    if (file != null && (file.isFile() || Arrays.toString(array).equals(""))) {
        /* ... */
      }

  }
}
