// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siardsuite.ui.presenter;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URL;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 진행 화면 세 곳이 열리는지 본다. 화면 정의(fxml)는 컴파일이 잡아 주지 않아 열어 봐야 안다.
 */
class ProgressViewsFxmlTest {

    @BeforeAll
    static void startToolkit() throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(ready::countDown);
        } catch (IllegalStateException alreadyStarted) {
            ready.countDown();
        }
        if (!ready.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX 툴킷이 30초 안에 준비되지 않았다");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "fxml/archive/archive-loading-preview.fxml",
            "fxml/archive/archive-download.fxml",
            "fxml/upload/upload-uploading.fxml"
    })
    void screenOpens(String path) throws Exception {
        URL url = Objects.requireNonNull(
                getClass().getClassLoader().getResource("ch/admin/bar/siardsuite/" + path), path);

        AtomicReference<Parent> loaded = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                loaded.set(new FXMLLoader(url).load());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(60, TimeUnit.SECONDS), "화면이 60초 안에 열리지 않았다: " + path);
        if (failure.get() != null) {
            throw new IllegalStateException("화면을 열지 못했다: " + path, failure.get());
        }
        assertNotNull(loaded.get(), path);
    }
}
