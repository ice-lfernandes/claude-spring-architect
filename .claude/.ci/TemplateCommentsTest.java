///usr/bin/env java --source 21 "$0" "$@" ; exit $?
//
// CI test: proves no Java exemplar carries a comment in its body that a generated project's
// Checkstyle would reject — `code-quality.md` § Comments, "Javadoc is the only comment".
//
// Why this test exists: the executor copies an exemplar's shape, comments included, and 60
// templates once carried 508 body comments between them (issue #62, decision 0108). The
// generated project's build now rejects them, so a comment that returns to a template is
// a red `check` hook in every project that copies it — found there, not here, unless this
// step finds it first.
//
// What it reads, so the two never disagree: the `format` of the `LineComment` and
// `BlockComment` modules and the `legalComment` of `TrailingComment`, straight from
// `project-bootstrap/templates/checkstyle.xml.example`. The leading comment block of each
// exemplar (the `EXEMPLAR` header, stripped when the exemplar is translated) is not body and
// is skipped, and so is a `// --- ` marker at column 0: it separates the files or sections of
// a multi-file exemplar and is never copied. A `/** */` inside a body
// (`InvalidJavadocPosition`) needs a parser and is left to the project's build.
//
// Out of scope: `claude-code-architect-designer/templates/`, whose Java exemplar is the shape
// of an `ArchHook.java` mode, not of generated code.

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

public class TemplateCommentsTest {

    static final Path SKILLS = Paths.get(".claude/skills");
    static final Path CHECKSTYLE = SKILLS.resolve("project-bootstrap/templates/checkstyle.xml.example");
    static final String OUT_OF_SCOPE = "claude-code-architect-designer/templates/";
    static final String SECTION = "// --- ";

    public static void main(String[] args) throws Exception {
        String config = Files.readString(CHECKSTYLE, StandardCharsets.UTF_8);
        Pattern line = Pattern.compile(moduleProperty(config, "LineComment", "format"), Pattern.MULTILINE);
        Pattern block = Pattern.compile(moduleProperty(config, "BlockComment", "format"), Pattern.MULTILINE);
        Pattern legal = Pattern.compile(trailingLegalComment(config));

        List<Path> exemplars;
        try (Stream<Path> s = Files.walk(SKILLS)) {
            exemplars = s.filter(p -> p.toString().endsWith(".java.example"))
                    .filter(p -> !SKILLS.relativize(p).toString().replace('\\', '/').contains(OUT_OF_SCOPE))
                    .sorted().toList();
        }

        List<String> violations = new ArrayList<>();
        for (Path file : exemplars) {
            String text = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
            int body = headerEnd(text);
            String rel = file.toString().replace('\\', '/');
            find(line, text, body).forEach(n -> violations.add(rel + ":" + n + "  // on its own line"));
            find(block, text, body).forEach(n -> violations.add(rel + ":" + n + "  /* */ on its own line"));
            trailing(text, body, legal).forEach(n -> violations.add(rel + ":" + n + "  comment after code"));
        }

        if (!violations.isEmpty()) {
            violations.forEach(v -> System.out.println("  ✗ " + v));
            System.out.println("❌ " + violations.size() + " comment(s) in exemplar bodies. code-quality.md § Comments:"
                    + " Javadoc is the only comment. Move an instruction to the model into the EXEMPLAR header,"
                    + " a design reason into the Javadoc of the type or method, and drop what only describes the code.");
            System.exit(1);
        }
        System.out.println("✅ " + exemplars.size() + " Java exemplars carry no body comment the generated"
                + " project's Checkstyle would reject.");
    }

    /** The `value` of one `<property>` inside the module whose `id` is the given one, XML-unescaped. */
    static String moduleProperty(String config, String id, String name) {
        Matcher m = Pattern.compile("<module name=\"RegexpMultiline\">(.*?)</module>", Pattern.DOTALL).matcher(config);
        while (m.find()) {
            String module = m.group(1);
            if (module.contains("name=\"id\" value=\"" + id + "\"")) return property(module, name);
        }
        throw new IllegalStateException("no RegexpMultiline with id " + id + " in " + CHECKSTYLE);
    }

    static String trailingLegalComment(String config) {
        Matcher m = Pattern.compile("<module name=\"TrailingComment\">(.*?)</module>", Pattern.DOTALL).matcher(config);
        if (!m.find()) throw new IllegalStateException("no TrailingComment in " + CHECKSTYLE);
        return property(m.group(1), "legalComment");
    }

    static String property(String module, String name) {
        Matcher p = Pattern.compile("name=\"" + name + "\"\\s+value=\"([^\"]*)\"").matcher(module);
        if (!p.find()) throw new IllegalStateException("no property " + name + " in " + module);
        return p.group(1).replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&");
    }

    /** Offset of the first character after the leading comment block and the blank lines around it. */
    static int headerEnd(String text) {
        int i = 0;
        while (true) {
            int start = i;
            while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
            if (text.startsWith("//", i)) {
                int nl = text.indexOf('\n', i);
                i = nl < 0 ? text.length() : nl + 1;
            } else if (text.startsWith("/*", i) && !text.startsWith("/**", i)) {
                int end = text.indexOf("*/", i + 2);
                i = end < 0 ? text.length() : end + 2;
            } else {
                return start;
            }
        }
    }

    static List<Integer> find(Pattern pattern, String text, int from) {
        List<Integer> lines = new ArrayList<>();
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            if (m.start() < from || text.startsWith(SECTION, lineStart(text, m.start()))) continue;
            lines.add(lineOf(text, m.start()));
        }
        return lines;
    }

    /**
     * Lines where a `//` or a `/*` follows code. A small lexer, not a regex: a `//` inside a
     * string literal, a text block or a Javadoc line is not a comment, and only the lexer
     * knows which state it is in.
     */
    static List<Integer> trailing(String text, int from, Pattern legal) {
        List<Integer> lines = new ArrayList<>();
        boolean codeOnLine = false;
        int i = from;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\n') {
                codeOnLine = false;
                i++;
            } else if (text.startsWith("\"\"\"", i)) {
                int end = text.indexOf("\"\"\"", i + 3);
                i = end < 0 ? text.length() : end + 3;
                codeOnLine = true;
            } else if (c == '"' || c == '\'') {
                i = skipLiteral(text, i, c);
                codeOnLine = true;
            } else if (text.startsWith("//", i)) {
                int nl = text.indexOf('\n', i);
                int end = nl < 0 ? text.length() : nl;
                if (codeOnLine && !legal.matcher(text.substring(i + 2, end)).find()) lines.add(lineOf(text, i));
                i = end;
            } else if (text.startsWith("/*", i)) {
                if (codeOnLine && !text.startsWith("/**", i)) lines.add(lineOf(text, i));
                int end = text.indexOf("*/", i + 2);
                i = end < 0 ? text.length() : end + 2;
            } else {
                if (!Character.isWhitespace(c)) codeOnLine = true;
                i++;
            }
        }
        return lines;
    }

    static int skipLiteral(String text, int i, char quote) {
        int j = i + 1;
        while (j < text.length() && text.charAt(j) != quote && text.charAt(j) != '\n') {
            j += text.charAt(j) == '\\' ? 2 : 1;
        }
        return j + 1;
    }

    static int lineStart(String text, int offset) {
        return text.lastIndexOf('\n', offset - 1) + 1;
    }

    static int lineOf(String text, int offset) {
        int n = 1;
        for (int k = 0; k < offset; k++) if (text.charAt(k) == '\n') n++;
        return n;
    }
}
