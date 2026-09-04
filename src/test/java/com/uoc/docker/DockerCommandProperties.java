package com.uoc.docker;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Container names are the only handle the launcher has on the services it
 * starts, and
 * they travel both ways: the key becomes a name when a command is built, and a
 * name
 * becomes a key when Docker reports an event. The two directions have to agree,
 * which is
 * what a property expresses better than a handful of examples.
 */
class DockerCommandProperties {

    @Property
    void aKeySurvivesTheTripThroughAContainerName(
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) String key) {
        assertThat(DockerCommand.serviceKey(DockerCommand.containerName(key))).isEqualTo(key);
    }

    @Property
    void everyNameTheLauncherBuildsIsRecognisedAsItsOwn(
            @ForAll @NotBlank @StringLength(min = 1, max = 20) String key) {
        assertThat(DockerCommand.isManagedContainer(DockerCommand.containerName(key))).isTrue();
    }

    @Property
    void aNameWithoutThePrefixIsNotOurs(
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) String name) {
        assertThat(DockerCommand.isManagedContainer(name)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(Database.class)
    void everyDatabaseContainerIsRecognisedAndMapsBack(Database database) {
        String name = database.containerName();

        assertThat(DockerCommand.isManagedContainer(name)).isTrue();
        assertThat(DockerCommand.serviceKey(name)).isEqualTo(database.key());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "mongo", "uocdb", "uocd-mongo", "myuocdb-mongo", "UOCDB-mongo", "", " "
    })
    void containersThatBelongToSomebodyElseAreIgnored(String name) {
        // The event stream carries every container on the machine, including the ones
        // the
        // student runs for their own work. Acting on those would be wrong.
        assertThat(DockerCommand.isManagedContainer(name)).isFalse();
    }

    @Test
    void aMissingNameIsNotOurs() {
        assertThat(DockerCommand.isManagedContainer(null)).isFalse();
    }

    @Test
    void aNameThatIsExactlyThePrefixLeavesAnEmptyKey() {
        // The boundary of the prefix: one character shorter and it is not ours at all.
        assertThat(DockerCommand.serviceKey("uocdb-")).isEmpty();
        assertThat(DockerCommand.isManagedContainer("uocdb-")).isTrue();
        assertThat(DockerCommand.isManagedContainer("uocdb")).isFalse();
    }

    @Test
    void aNameThatIsNotOursIsReturnedUnchanged() {
        assertThat(DockerCommand.serviceKey("otro-contenedor")).isEqualTo("otro-contenedor");
    }
}
