// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siardsuite.ui.component;

import ch.admin.bar.siardsuite.util.I18n;
import org.junit.Before;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

import static ch.admin.bar.siardsuite.ui.component.ButtonBox.Type.*;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Locale;

@ExtendWith(ApplicationExtension.class)
public class ButtonBoxTest {

    /**
     * 이 시험은 영문 라벨을 기대한다. 로케일을 고정하지 않으면 장비의 기본 로케일을
     * 따라가는데, 한국어가 언어로 추가된 뒤로 한국어 장비에서만 깨졌다.
     */
    @BeforeAll
    static void useEnglishLabels() {
        I18n.setLocale(Locale.ENGLISH);
    }

    @Before
    public void setUpHeadlessMode() {
        System.setProperty("java.awt.headless", "true");
        System.setProperty("testfx.headless", "true");
    }

    @Test
    void shouldCreateDefaultButtonBox() {
        // given

        // when
        ButtonBox buttonBox = new ButtonBox().make(DEFAULT);

        // then
        assertNotNull(buttonBox);
        assertThat(buttonBox.getChildren().size(), is(3));
        assertThat(buttonBox.getChildren(), hasItems(buttonBox.cancelButton, buttonBox.nextButton, buttonBox.previousButton));
        assertThat(buttonBox.cancelButton.getText(), is("Cancel"));
        assertThat(buttonBox.nextButton.getText(), is("Next"));
        assertThat(buttonBox.previousButton.getText(), is("Back"));
    }

    @Test
    void shouldCreateCancelButtonBox() {
        // given

        // when
        ButtonBox buttonBox = new ButtonBox().make(CANCEL);

        // then
        assertNotNull(buttonBox);
        assertThat(buttonBox.getChildren().size(), is(1));
        assertThat(buttonBox.getChildren().get(0), is(buttonBox.cancelButton));
        assertThat(buttonBox.cancelButton.getText(), is("Cancel"));
    }
}
