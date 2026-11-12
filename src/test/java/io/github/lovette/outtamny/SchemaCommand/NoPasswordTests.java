package io.github.lovette.outtamny.SchemaCommand;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NoPasswordTests extends SchemaCommandTestBase {

    @BeforeEach
    void setMnyPath() {
        mnyPath = resourcePath("sunset-sample.mny");
    }

    @Test
    @DisplayName("schema quiet")
    public void testQuiet() throws IOException {
        executeSchemaCommand();
        assertThat(stdout()).isEmpty();
        assertThat(listOutDirContents()).containsOnly("sunset-sample.sql");
    }

    @Test
    @DisplayName("schema --verbose")
    public void testVerbose() throws IOException {
        executeSchemaCommand("-vv");
        assertThat(stdout()).isNotEmpty();
        assertThat(listOutDirContents()).containsOnly("sunset-sample.sql");
    }
}
