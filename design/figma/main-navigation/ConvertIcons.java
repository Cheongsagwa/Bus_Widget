import com.android.ide.common.vectordrawable.Svg2Vector;
import java.nio.file.*;

/** Converts the original Figma SVGs with Android's own vector importer. */
class ConvertIcons {
    public static void main(String[] args) throws Exception {
        try (var files = Files.list(Path.of(args[0]))) {
            for (var source : files.filter(p -> p.toString().endsWith(".svg")).toList()) {
                var name = "ic_nav_" + source.getFileName().toString().replace(".svg", ".xml");
                try (var output = Files.newOutputStream(Path.of(args[1], name))) {
                    var errors = Svg2Vector.parseSvgToXml(source, output);
                    if (!errors.isBlank()) throw new IllegalStateException(source + ": " + errors);
                }
                System.out.println(name);
            }
        }
    }
}
