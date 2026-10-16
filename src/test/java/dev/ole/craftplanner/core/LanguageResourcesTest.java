package dev.ole.craftplanner.core;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import static org.junit.jupiter.api.Assertions.*;
class LanguageResourcesTest {
    private JsonObject language(String id)throws Exception { return JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/craftplanner/lang/"+id+".json"))).getAsJsonObject(); }
    @Test void languagesHaveSameKeysAndPlaceholders()throws Exception {
        var de=language("de_de"); var en=language("en_us"); assertEquals(de.keySet(),en.keySet()); assertTrue(en.size()>130);
        for(String key:en.keySet()) {
            assertFalse(de.get(key).getAsString().isBlank(),key); assertEquals(placeholders(en.get(key).getAsString()),placeholders(de.get(key).getAsString()),key);
        }
    }
    private List<String> placeholders(String s) { return Pattern.compile("%(?:[0-9]+\\$)?[sd]").matcher(s).results().map(MatchResult::group).sorted().toList(); }
    @Test void noMissingLiteralTranslationKeysOrRemovedSlogan()throws Exception {
        var en=language("en_us");
        try(var paths=Files.walk(Path.of("src/client/java"))) {
            for(var path:paths.filter(p->p.toString().endsWith(".java")).toList()) {
                String s=Files.readString(path); assertFalse(s.contains("Sammeln. Craften. Fertig."));
                var m=Pattern.compile("Lang\\.(?:tr|text)\\(\"((?:craftplanner|key\\.craftplanner)\\.[^\"]+)\"").matcher(s);
                while(m.find())if(!m.group(1).endsWith("."))assertTrue(en.has(m.group(1)),path+": "+m.group(1));
            }
        }
    }
}
