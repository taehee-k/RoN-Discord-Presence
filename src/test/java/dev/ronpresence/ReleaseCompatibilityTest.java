package dev.ronpresence;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import java.nio.file.Path;
import java.net.URLClassLoader;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.objectweb.asm.Opcodes.*;

/** Checks the actual distributed release without loading Minecraft client classes. */
class ReleaseCompatibilityTest {
    private static final String ROOT = "com/solegendary/reignofnether/";

    private Path release() {
        String path = System.getProperty("ronReleaseJar");
        assumeTrue(path != null, "Supply -PronReleaseJar=/path/to/reignofnether-1.5.0a.jar to run release checks");
        return Path.of(path);
    }

    @Test void allReflectedMembersExistInOfficialRelease() throws Exception {
        try (var jar = new JarFile(release().toFile())) {
            assertEquals("1.5.0a", jar.getManifest().getMainAttributes().getValue("Implementation-Version"));
            method(jar, "player/PlayerClientEvents", "getRTSPlayer", "()L" + ROOT + "player/RTSPlayer;");
            method(jar, "orthoview/OrthoviewClientEvents", "isEnabled", "()Z");
            method(jar, "tutorial/TutorialClientEvents", "isEnabled", "()Z");
            field(jar, "gamemode/ClientGameModeHelper", "gameMode", "L" + ROOT + "gamemode/GameMode;", true);
            field(jar, "gamemode/ClientGameModeHelper", "gameModeLocked", "Z", true);
            field(jar, "player/RTSPlayer", "faction", "L" + ROOT + "faction/Faction;", false);
            field(jar, "startpos/StartPosClientEvents", "selectedFaction", "L" + ROOT + "faction/Faction;", true);
            field(jar, "startpos/StartPosClientEvents", "isStarting", "Z", true);
            field(jar, "survival/SurvivalClientEvents", "waveNumber", "I", true);
            field(jar, "rtsmap/RTSMapInfoClientEvents", "mapName", "Ljava/lang/String;", true);
            method(jar, "player/PlayerClientEvents", "syncRtsGameTime", "(Ljava/lang/Long;)V");
            method(jar, "player/PlayerClientEvents", "resetRTS", "(Z)V");
            method(jar, "startpos/StartPosClientEvents", "getPos", "()L"+ROOT+"startpos/StartPos;");
            method(jar, "alliance/AlliancesClient", "getAllAllies", "(Ljava/lang/String;)Ljava/util/Set;");
            method(jar, "time/TimeClientEvents", "isBloodMoonActive", "()Z");
            method(jar, "tutorial/TutorialClientEvents", "getStage", "()L"+ROOT+"tutorial/TutorialStage;");
            field(jar, "player/PlayerClientEvents", "rtsPlayers", "Ljava/util/List;", true);
            field(jar, "player/PlayerClientEvents", "rtsGameTicks", "J", true);
            field(jar, "player/RTSPlayer", "name", "Ljava/lang/String;", false);
            field(jar, "startpos/StartPos", "ready", "Z", false);
            field(jar, "time/TimeClientEvents", "serverNormDayTime", "J", true);
            field(jar, "gamerules/GameruleClient", "coopMode", "Z", true);
            assertEquals(0, read(jar, "faction/Faction").access & ACC_ENUM);
            field(jar, "faction/Faction", "key", "Lnet/minecraft/resources/ResourceLocation;", false);
            assertTrue(read(jar, "faction/Faction").methods.stream().anyMatch(m -> m.name.equals("getName")
                    && m.desc.equals("()Ljava/lang/String;") && (m.access & ACC_PUBLIC) != 0 && (m.access & ACC_STATIC) == 0));
            assertNotEquals(0, read(jar, "gamemode/GameMode").access & ACC_ENUM);
        }
    }

    @Test void registryFactionNamesIncludeCustomFactionsAndNone() {
        for (String name : new String[]{"villagers", "monsters", "piglins", "neutral", "random", "none", "frost_elves"}) {
            assertEquals(name, RonBridge.factionName(name));
        }
        assertEquals("", RonBridge.factionName(null));
        assertEquals("", PresenceState.title(RonBridge.factionName("none")));
        assertEquals("Frost Elves", PresenceState.title(RonBridge.factionName("frost_elves")));
    }

    private ClassNode read(JarFile jar, String name) throws Exception {
        var entry = jar.getJarEntry(ROOT + name + ".class");
        assertNotNull(entry, name);
        try (var input = jar.getInputStream(entry)) {
            var node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private void method(JarFile jar, String type, String name, String descriptor) throws Exception {
        assertTrue(read(jar, type).methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(descriptor)
                && (m.access & (ACC_PUBLIC | ACC_STATIC)) == (ACC_PUBLIC | ACC_STATIC)), type + "." + name);
    }

    private void field(JarFile jar, String type, String name, String descriptor, boolean isStatic) throws Exception {
        assertTrue(read(jar, type).fields.stream().anyMatch(f -> f.name.equals(name) && f.desc.equals(descriptor)
                && (f.access & ACC_PUBLIC) != 0 && ((f.access & ACC_STATIC) != 0) == isStatic), type + "." + name);
    }
}
