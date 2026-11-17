package io.github.lovette.outtamny.TablesCommand;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class TablesTests extends TablesCommandTestBase {
    @Test
    @DisplayName("tables")
    void testTablesOut() {
        executeTablesCommand();

        assertThat(stderr()).isEmpty();
        assertThat(stdoutTableNames()).hasSize(83).contains("ACCT").contains("TRN");
    }
}
