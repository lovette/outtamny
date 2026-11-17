package io.github.lovette.outtamny.SchemaCommand;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.org.webcompere.systemstubs.SystemStubs.tapSystemOut;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariable;
import static uk.org.webcompere.systemstubs.SystemStubs.withTextFromSystemIn;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class WithPasswordTests extends SchemaCommandTestBase {

    private String mnyPassword = "123@abc!";

    @BeforeEach
    void setMnyPath() {
        mnyPath = resourcePath("sunset-sample-pwd.mny");
    }

    @Test
    @DisplayName("schema --password:pw")
    public void testPasswordArgument() throws IOException {
        executeSchemaCommand("-vv", "--password:pw", mnyPassword);

        assertThat(stdout()).isNotEmpty();
        assertThat(listOutDirContents()).containsOnly("sunset-sample-pwd.sql");
    }

    @Test
    @DisplayName("schema --password interactive")
    public void testPasswordInteractive() throws Exception {
        // picocli writes everything to its 'setOut' writer *except* interactive
        // prompts, which go to the console directly.
        String consoleOut = tapSystemOut(() -> withTextFromSystemIn(mnyPassword).execute(() -> {
            executeSchemaCommand("-vv", "--password");
        }));

        assertThat(stdout()).isNotEmpty();
        assertThat(consoleOut).startsWith("Enter the password for the Money data file:");
        assertThat(listOutDirContents()).containsOnly("sunset-sample-pwd.sql");
    }

    @Test
    @DisplayName("schema --password:env")
    public void testPasswordEnvVar() throws Exception {
        withEnvironmentVariable("JUNITMNYPASSWORD", mnyPassword).execute(() -> {
            executeSchemaCommand("-vv", "--password:env", "JUNITMNYPASSWORD");
        });

        assertThat(stdout()).isNotEmpty();
        assertThat(listOutDirContents()).containsOnly("sunset-sample-pwd.sql");
    }

    @Test
    @DisplayName("schema --password:env missing")
    public void testPasswordEnvVarMissing() {
        executeSchemaCommand(1, "-vv", "--password:env", "JUNITMNYPASSWORD");

        assertThat(stdout()).isEmpty();
        assertThat(stderr()).startsWith("Error:").endsWith("is not set.");
    }

    @Test
    @DisplayName("schema --password:file")
    public void testPasswordFile() throws IOException {
        Path passwordFilePath = tempOutDir.resolve("sunset-sample-pwd.txt");
        Files.writeString(passwordFilePath, mnyPassword);

        executeSchemaCommand("-vv", "--password:file", passwordFilePath.toString());

        assertThat(stdout()).isNotEmpty();
        assertThat(listOutDirContents()).containsOnly("sunset-sample-pwd.txt", "sunset-sample-pwd.sql");
    }

    @Test
    @DisplayName("schema --password:file missing")
    public void testPasswordFileMissing() throws IOException {
        Path passwordFilePath = tempOutDir.resolve("sunset-sample-pwd.txt");

        executeSchemaCommand(1, "-vv", "--password:file", passwordFilePath.toString());

        assertThat(stdout()).isEmpty();
        assertThat(stderr()).startsWith("Error:").endsWith(": No such file.");
    }

    @Test
    @DisplayName("schema wrong password")
    public void testWrongPassword() throws IOException {
        Path passwordFilePath = tempOutDir.resolve("sunset-sample-pwd.txt");
        Files.writeString(passwordFilePath, "foo");

        executeSchemaCommand(1, "-vv", "--password:file", passwordFilePath.toString());

        assertThat(stdout()).isEmpty();
        assertThat(stderr()).isEqualTo("Error: Invalid password for the Money data file.");
    }
}
