package io.github.lovette.outtamny.SchemaCommand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class OutCombinationTests extends SchemaCommandTestBase {

    @BeforeEach
    void setMnyPath() {
        mnyPath = resourcePath("sunset-sample.mny");
    }

    // Create a stream of '--schema-out' and '--data-out' combinations for each
    // 'SchemaCommand.OutputScheme' except for the combination where both are
    // 'none'.
    static Stream<Arguments> outputCombinations() {
        var values = List.of("combined", "split", "table_combined", "table", "none");
        return values.stream()
                .flatMap(schema -> values.stream()
                        .filter(data -> !(schema.equals("none") && data.equals("none")))
                        .map(data -> Arguments.of(schema, data)));
    }

    @Test
    @DisplayName("schema and data output both set to none")
    void testOutputCombinationNone() {
        String[] args = {"-vv", "--schema-out", "none", "--data-out", "none"};
        executeSchemaCommand(1, args);

        assertThat(stdout()).isEmpty();
        assertThat(stderr()).contains("Options '--schema-out' and '--data-out' cannot both be set to 'none'.");
    }

    @ParameterizedTest(name = "schema={0}, data={1}")
    @MethodSource("outputCombinations")
    void testOutputCombination(String schema, String data) throws IOException {
        String[] args = {"-vv", "--schema-out", schema, "--data-out", data};

        executeSchemaCommand(args);

        List<String> outDirContents = listOutDirContents();

        if (schema.equals("combined") && (data.equals("combined") || data.equals("none"))) {
            assertThat(outDirContents).containsOnly("sunset-sample.sql");
        } else if (schema.equals("none") && (data.equals("combined") || data.equals("split"))) {
            assertThat(outDirContents).containsOnly("sunset-sample.sql");
        } else {
            assertThat(outDirContents).containsOnly("sunset-sample.sql", "sunset-sample/");

            List<String> outSubDirContents = listOutDirFiles(Paths.get("sunset-sample/"));

            switch (schema) {
                case "combined":
                    switch (data) {
                        case "split":
                            assertThat(outSubDirContents).containsOnly("tables.sql", "data.sql");

                            break;
                        case "table_combined", "table":
                            {
                                assertThat(outSubDirContents)
                                        .hasSize(34)
                                        .contains("tables.sql")
                                        .doesNotContain("indexes.sql", "data.sql");
                            }
                            break;
                        default:
                            fail(String.format("Unexpected combination: schema='%s', data='%s'", schema, data));
                    }
                    break;
                case "split":
                    switch (data) {
                        case "combined", "split":
                            assertThat(outSubDirContents).containsOnly("tables.sql", "indexes.sql", "data.sql");
                            break;
                        case "none":
                            assertThat(outSubDirContents).containsOnly("tables.sql", "indexes.sql");
                            break;
                        case "table_combined", "table":
                            assertThat(outSubDirContents)
                                    .hasSize(35)
                                    .contains("tables.sql", "indexes.sql")
                                    .doesNotContain("data.sql");
                            break;
                        default:
                            fail(String.format("Unexpected combination: schema='%s', data='%s'", schema, data));
                    }
                    break;
                case "table_combined":
                    switch (data) {
                        case "table_combined", "none":
                            assertThat(outSubDirContents)
                                    .hasSize(83)
                                    .doesNotContain("tables.sql", "indexes.sql", "data.sql");
                            break;
                        case "combined", "split":
                            assertThat(outSubDirContents)
                                    .hasSize(84)
                                    .contains("data.sql")
                                    .doesNotContain("tables.sql", "indexes.sql");
                            break;
                        case "table":
                            assertThat(outSubDirContents)
                                    .hasSize(116)
                                    .doesNotContain("tables.sql", "indexes.sql", "data.sql");
                            break;
                        default:
                            fail(String.format("Unexpected combination: schema='%s', data='%s'", schema, data));
                    }
                    break;
                case "table":
                    switch (data) {
                        case "none":
                            assertThat(outSubDirContents)
                                    .hasSize(144)
                                    .doesNotContain("tables.sql", "indexes.sql", "data.sql");
                            break;
                        case "combined", "split":
                            assertThat(outSubDirContents)
                                    .hasSize(145)
                                    .contains("data.sql")
                                    .doesNotContain("tables.sql", "indexes.sql");
                            break;
                        case "table_combined", "table":
                            assertThat(outSubDirContents)
                                    .hasSize(177)
                                    .doesNotContain("tables.sql", "indexes.sql", "data.sql");
                            break;
                        default:
                            fail(String.format("Unexpected combination: schema='%s', data='%s'", schema, data));
                    }
                    break;
                case "none":
                    switch (data) {
                        case "table_combined", "table":
                            assertThat(outSubDirContents)
                                    .hasSize(33)
                                    .doesNotContain("tables.sql", "indexes.sql", "data.sql");
                            break;
                        default:
                            fail(String.format("Unexpected combination: schema='%s', data='%s'", schema, data));
                    }
                    break;
                default:
                    fail(String.format("Unexpected combination: schema='%s', data='%s'", schema, data));
            }
        }
    }
}
