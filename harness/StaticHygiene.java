import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.ToolProvider;

/** Supplied, narrow static sensor for a library with no console interface. */
final class StaticHygiene {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Pass the production Java source root");
        var root = Path.of(args[0]).toAbsolutePath();
        try (var walk = Files.walk(root)) {
            var paths = walk.filter(path -> path.toString().endsWith(".java")).sorted().toList();
            if (paths.isEmpty()) throw new IllegalStateException("No production Java files to inspect");
            var compiler = ToolProvider.getSystemJavaCompiler();
            try (var files = compiler.getStandardFileManager(null, null, null)) {
                var sources = files.getJavaFileObjectsFromPaths(paths);
                var task = (JavacTask) compiler.getTask(null, files, null,
                        List.of("-proc:none"), null, sources);
                var positions = Trees.instance(task).getSourcePositions();
                var violations = new int[] {0};
                for (CompilationUnitTree unit : task.parse()) {
                    new TreeScanner<Void, Void>() {
                        @Override public Void visitMemberSelect(MemberSelectTree select, Void unused) {
                            var owner = select.getExpression().toString();
                            if ((owner.equals("System") || owner.equals("java.lang.System"))
                                    && (select.getIdentifier().contentEquals("out")
                                        || select.getIdentifier().contentEquals("err"))) {
                                var position = positions.getStartPosition(unit, select);
                                var line = unit.getLineMap().getLineNumber(position);
                                var path = Path.of(unit.getSourceFile().toUri());
                                System.out.println("[LINT] " + root.relativize(path) + ":" + line
                                        + ": Bookshelf library code must not write directly to the console; "
                                        + "return an outcome or let the caller handle logging.");
                                violations[0]++;
                            }
                            return super.visitMemberSelect(select, unused);
                        }
                    }.scan(unit, null);
                }
                if (violations[0] > 0) System.exit(1);
            }
        }
    }
}
