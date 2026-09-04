package com.uoc.docker;

import com.uoc.i18n.Message;
import com.uoc.i18n.Translations;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceStatusTest {

    @Test
    void onlyTheStatusesThatAreGoingSomewherePulse() {
        // The circle pulses to say "wait, this is moving". A settled status must not,
        // and
        // a failure must not either: a blinking red dot reads as activity, not as a
        // stop.
        assertThat(ServiceStatus.values()).filteredOn(ServiceStatus::isTransitional)
                .containsExactlyInAnyOrder(
                        ServiceStatus.STARTING, ServiceStatus.RUNNING, ServiceStatus.STOPPING);
    }

    @Test
    void theFailuresAreTheOnesTheStudentHasToActOn() {
        assertThat(ServiceStatus.values()).filteredOn(ServiceStatus::isFailure)
                .containsExactlyInAnyOrder(ServiceStatus.UNHEALTHY, ServiceStatus.ERROR);
    }

    @ParameterizedTest
    @EnumSource(ServiceStatus.class)
    void noStatusIsBothOnTheMoveAndAFailure(ServiceStatus status) {
        assertThat(status.isTransitional() && status.isFailure())
                .as("%s cannot be both", status)
                .isFalse();
    }

    @ParameterizedTest
    @EnumSource(ServiceStatus.class)
    void everyStatusCanBeShownToTheStudent(ServiceStatus status) {
        assertThat(status.message()).as("%s has no text", status).isNotNull();
    }

    @Test
    void everyStatusHasItsOwnText() {
        assertThat(ServiceStatus.values()).extracting(ServiceStatus::message).doesNotHaveDuplicates();
    }

    @ParameterizedTest
    @EnumSource(ServiceStatus.class)
    void everyStatusReadsAsSomethingInEveryLanguage(ServiceStatus status) {
        for (Locale locale : new Locale[] { Locale.ENGLISH, Locale.of("es"), Locale.of("ca") }) {
            Translations translations = new Translations(locale);
            assertThat(translations.get(status.message()))
                    .as("%s in %s", status, locale)
                    .isNotBlank();
        }
    }

    @ParameterizedTest
    @EnumSource(ServiceStatus.class)
    void theTextOfAStatusIsTheOneNamedAfterIt(ServiceStatus status) {
        // A copy-paste in the enum would otherwise show "stopping" where "starting"
        // belongs.
        Message message = status.message();
        assertThat(message.key()).isEqualTo("status." + status.name().toLowerCase(Locale.ROOT));
    }
}
