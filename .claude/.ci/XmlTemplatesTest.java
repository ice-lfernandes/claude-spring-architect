///usr/bin/env java --source 21 "$0" "$@" ; exit $?
//
// CI test: proves every XML exemplar under `.claude/skills/` is a well-formed XML document —
// the JDK's own parser, the same family Logback, Maven and Checkstyle load these files with.
//
// Why this test exists: `logback-spring.xml.example` opened with its EXEMPLAR comment and put
// `<?xml … ?>` at line 23. A declaration that is not the first thing in the document is a fatal
// parse error, the bootstrap copied the order, and every test that starts a Spring context went
// red in the generated project (lessons-learned-020 § 1, decision 0123). Nothing here parsed the
// file, so it was found there and patched by hand, run after run.
//
// A fragment — no declaration, merged into a file that already exists — is parsed under a
// synthetic root, so its markup is still checked.
//
// Offline and in milliseconds: no Initializr project, no Maven. What it does not prove is that a
// parsed file is the configuration the tool expects — that stays with the tests that build a
// project (`templates.yml`).

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

public class XmlTemplatesTest {

    static final Path SKILLS = Paths.get(".claude/skills");

    public static void main(String[] args) throws Exception {
        List<Path> exemplars;
        try (Stream<Path> s = Files.walk(SKILLS)) {
            exemplars = s.filter(p -> p.toString().endsWith(".xml.example")).sorted().toList();
        }
        if (exemplars.isEmpty()) {
            System.out.println("❌ No *.xml.example under " + SKILLS + " — run from the repository root.");
            System.exit(1);
        }

        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        List<String> failures = new ArrayList<>();
        for (Path file : exemplars) {
            var builder = f.newDocumentBuilder();
            builder.setErrorHandler(new ErrorHandler() {
                public void warning(SAXParseException e) {}
                public void error(SAXParseException e) throws SAXParseException { throw e; }
                public void fatalError(SAXParseException e) throws SAXParseException { throw e; }
            });
            String text = Files.readString(file, StandardCharsets.UTF_8);
            // A file with a declaration is a whole document and parses as one — that is what
            // catches a declaration below a comment. One without is a fragment merged into a
            // file that already exists (a POM's <properties> and <build>), so it gets a
            // synthetic root and is checked for well-formed markup only.
            String doc = text.contains("<?xml") ? text : "<fragment>" + text + "</fragment>";
            try {
                builder.parse(new ByteArrayInputStream(doc.getBytes(StandardCharsets.UTF_8)));
            } catch (SAXParseException e) {
                failures.add(file.toString().replace('\\', '/') + ":" + e.getLineNumber() + "  " + e.getMessage());
            }
        }

        if (!failures.isEmpty()) {
            failures.forEach(v -> System.out.println("  ✗ " + v));
            System.out.println("❌ " + failures.size() + " XML exemplar(s) do not parse. A declaration goes on line 1,"
                    + " before the EXEMPLAR comment; the generated file keeps that order.");
            System.exit(1);
        }
        System.out.println("✅ " + exemplars.size() + " XML exemplars parse as well-formed documents.");
    }
}
