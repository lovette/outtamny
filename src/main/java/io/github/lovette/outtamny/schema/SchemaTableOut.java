package io.github.lovette.outtamny.schema;

import io.github.spannm.jackcess.Column;
import io.github.spannm.jackcess.Table;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Represents the output configuration for a database table, including schema, index, and data
 * output paths.
 */
class SchemaTableOut {
    final Table table;
    final List<? extends Column> columns;

    @Nullable
    final SchemaTableOutWriter schemaWriter;

    @Nullable
    final SchemaTableOutWriter indexWriter;

    @Nullable
    final SchemaTableOutWriter dataWriter;

    public boolean schemaWritten = false;
    public boolean indexWritten = false;
    public boolean dataWritten = false;

    /**
     * Constructs a TableOut instance for the specified table and output writers.
     *
     * @param table the database table
     * @param schemaWriter the writer for the table schema
     * @param indexWriter the writer for the table indexes
     * @param dataWriter the writer for the table data
     */
    public SchemaTableOut(
            Table table,
            @Nullable SchemaTableOutWriter schemaWriter,
            @Nullable SchemaTableOutWriter indexWriter,
            @Nullable SchemaTableOutWriter dataWriter) {
        this.table = table;
        this.columns = table.getColumns();

        this.schemaWriter = schemaWriter;
        if (this.schemaWriter != null) {
            this.schemaWriter.addTable(table.getName(), "schema");
        }

        this.indexWriter = indexWriter;
        if (this.indexWriter != null) {
            this.indexWriter.addTable(table.getName(), "index");
        }

        this.dataWriter = dataWriter;
        if (this.dataWriter != null) {
            this.dataWriter.addTable(table.getName(), "data");
        }
    }
}
