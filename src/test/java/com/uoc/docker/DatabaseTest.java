package com.uoc.docker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseTest {

    @ParameterizedTest
    @EnumSource(Database.class)
    void aDatabaseIsFoundBackFromItsOwnKey(Database database) {
        assertThat(Database.fromKey(database.key())).isSameAs(database);
    }

    @ParameterizedTest
    @EnumSource(Database.class)
    void everyDatabaseNamesItsContainerAfterItsKey(Database database) {
        // The compose file, the container name and the icon all follow from the key, so
        // they cannot drift apart.
        assertThat(database.containerName()).isEqualTo("uocdb-" + database.key());
        assertThat(database.iconResource()).isEqualTo("icons/" + database.key() + ".svg");
    }

    @ParameterizedTest
    @EnumSource(Database.class)
    void everyDatabaseShipsTheIconItAsksFor(Database database) {
        assertThat(getClass().getClassLoader().getResource(database.iconResource()))
                .as("missing icon for %s", database)
                .isNotNull();
    }

    @ParameterizedTest
    @EnumSource(Database.class)
    void everyDatabaseHasAKeyAndAName(Database database) {
        assertThat(database.key()).isNotBlank().isLowerCase();
        assertThat(database.displayName()).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(Database.class)
    void everyDatabaseNamesItsOwnConsole(Database database) {
        // Each console is driven with a different client and a different language, so
        // the
        // heading has to say which, in every language the interface offers.
        assertThat(database.consoleLabel()).as("%s has no console heading", database).isNotNull();

        for (java.util.Locale locale : new java.util.Locale[] {
                java.util.Locale.ENGLISH, java.util.Locale.of("es"), java.util.Locale.of("ca") }) {
            assertThat(new com.uoc.i18n.Translations(locale).get(database.consoleLabel()))
                    .as("%s has no console heading in %s", database, locale)
                    .isNotBlank();
        }
    }

    @Test
    void noTwoDatabasesShareAConsoleHeading() {
        // Two tabs with the same heading would leave the student unable to tell which
        // language they are meant to be writing.
        assertThat(Database.values()).extracting(Database::consoleLabel).doesNotHaveDuplicates();
    }

    @Test
    void keysAreUnique() {
        assertThat(Database.values()).extracting(Database::key).doesNotHaveDuplicates();
    }

    @Test
    void theCourseShowsThreeDatabasesAndOffersFourMoreServicesOnRequest() {
        assertThat(Database.values()).filteredOn(Database::isShownByDefault)
                .containsExactly(Database.MONGO, Database.CASSANDRA, Database.NEO4J);
        assertThat(Database.values()).filteredOn(d -> !d.isShownByDefault())
                .containsExactly(Database.NEO4J_TWITTER, Database.REDIS, Database.RIAK,
                        Database.JUPYTER);
    }

    @Test
    void jupyterIsTheOnlyServiceThatIsNotADatabase() {
        // Everything that sets it apart follows from this: no console to type at, and no
        // healthcheck to wait for.
        assertThat(Database.values()).filteredOn(d -> d.kind() == Database.Kind.NOTEBOOK)
                .containsExactly(Database.JUPYTER);
        assertThat(Database.JUPYTER.hasQueryConsole()).isFalse();
        assertThat(Database.JUPYTER.reportsHealth()).isFalse();
    }

    @Test
    void everyDatabaseIsQueriedAndJudgedByItsHealthcheck() {
        assertThat(Database.values()).filteredOn(d -> d.kind() == Database.Kind.DATABASE)
                .allMatch(Database::hasQueryConsole)
                .allMatch(Database::reportsHealth);
    }

    @Test
    void jupyterComesLastSoTheMenuCanGroupItApart() {
        assertThat(Database.values()[Database.values().length - 1]).isEqualTo(Database.JUPYTER);
    }

    @Test
    void theOnesShownByDefaultComeFirst() {
        // The menu draws its separator where this flag changes, so the order matters.
        boolean seenOptional = false;
        for (Database database : Database.values()) {
            if (!database.isShownByDefault()) {
                seenOptional = true;
            } else {
                assertThat(seenOptional)
                        .as("%s is shown by default but comes after one that is not", database)
                        .isFalse();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "MONGO", "Mongo", "mOnGo" })
    void aKeyIsMatchedWhateverItsCase(String key) {
        assertThat(Database.fromKey(key)).isSameAs(Database.MONGO);
    }

    @ParameterizedTest
    @ValueSource(strings = { "postgres", "mongodb", "mong", "mongo ", "sqlite" })
    void anUnknownKeyIsRejectedAndSaysWhichOne(String key) {
        assertThatThrownBy(() -> Database.fromKey(key))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(key);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void aMissingKeyIsRejectedRatherThanCrashing(String key) {
        assertThatThrownBy(() -> Database.fromKey(key))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
