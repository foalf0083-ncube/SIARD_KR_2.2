// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siardsuite.framework.i18n.helper;

import ch.admin.bar.siardsuite.SiardApplication;
import ch.admin.bar.siardsuite.framework.i18n.keys.Key;
import lombok.val;

import java.io.File;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class I18nTestHelper {

    static {
        try {
            Class<?> platform = Class.forName("javafx.application.Platform");
            java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(1);
            platform.getMethod("startup", Runnable.class).invoke(null, (Runnable) ready::countDown);
            if (!ready.await(30, java.util.concurrent.TimeUnit.SECONDS)) {
                System.err.println("[I18nTestHelper] JavaFX 툴킷이 30초 안에 준비되지 않았다");
            }
        } catch (Exception e) {
            Throwable cause = (e instanceof java.lang.reflect.InvocationTargetException)
                    ? e.getCause() : e;
            boolean alreadyStarted = cause instanceof IllegalStateException
                    && String.valueOf(cause.getMessage()).contains("already");
            if (!alreadyStarted) {
                System.err.println("[I18nTestHelper] JavaFX 툴킷 기동 실패: "
                        + cause.getClass().getName() + ": " + cause.getMessage());
                try {
                    Class<?> p = Class.forName("javafx.application.Platform");
                    System.err.println("[I18nTestHelper]   Platform 출처="
                            + p.getProtectionDomain().getCodeSource().getLocation());
                    for (java.lang.reflect.Method m : p.getMethods()) {
                        if (m.getName().equals("startup")) {
                            System.err.println("[I18nTestHelper]   startup 시그니처="
                                    + java.util.Arrays.toString(m.getParameterTypes()));
                        }
                    }
                } catch (Throwable ignore) {
                }
            }
        }
    }

    public static final List<ResourceBundle> BUNDLES = Arrays.asList(
            ResourceBundle.getBundle("ch/admin/bar/siardsuite/i18n/messages", Locale.KOREAN),
            ResourceBundle.getBundle("ch/admin/bar/siardsuite/i18n/messages", Locale.GERMAN),
            ResourceBundle.getBundle("ch/admin/bar/siardsuite/i18n/messages", Locale.FRENCH),
            ResourceBundle.getBundle("ch/admin/bar/siardsuite/i18n/messages", Locale.ITALIAN),
            ResourceBundle.getBundle("ch/admin/bar/siardsuite/i18n/messages", Locale.ENGLISH)
    );

    public static Set<Key> extractStaticKeyDefinitions() {
        val siardClasses = findAllClasses("ch.admin.bar.siardsuite");

        return siardClasses.stream()
                .flatMap(clazz -> extractStaticKeyDefinitions(clazz).stream())
                .collect(Collectors.toSet());
    }

    private static Set<Key> extractStaticKeyDefinitions(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .filter(field -> Key.class.isAssignableFrom(field.getType()))
                .map(field -> {
                    try {
                        field.setAccessible(true);
                        return (Key) field.get(null);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toSet());
    }

    private static Set<Class<?>> findAllClasses(String packageName) {
        return findAllClassNames(packageName).stream()
                .map(className -> {
                    try {
                        return SiardApplication.class.getClassLoader().loadClass(className);
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toSet());
    }

    private static Set<String> findAllClassNames(String packageName) {
        val mainBuildOutput = "./build/classes/java/main/".replace("/", File.separator);

        val classNames = findClassNames(new File(mainBuildOutput, packageName.replace(".", File.separator)))
                .collect(Collectors.toList());

        return classNames.stream()
                .map(s -> s.replace(mainBuildOutput, ""))
                .map(s -> s.replace(".class", ""))
                .map(s -> s.replace(File.separator, "."))
                .collect(Collectors.toSet());
    }

    private static Stream<String> findClassNames(File parentFile) {
        val childFiles = parentFile.listFiles();
        val classes = Arrays.stream(childFiles)
                .filter(File::isFile)
                .filter(childFile -> childFile.getName().endsWith(".class"))
                .map(File::toString);

        val classesFromSubDirs = Arrays.stream(childFiles)
                .filter(File::isDirectory)
                .flatMap(I18nTestHelper::findClassNames);

        return Stream.concat(classes, classesFromSubDirs);
    }
}