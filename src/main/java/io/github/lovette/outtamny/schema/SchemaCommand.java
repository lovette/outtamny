package io.github.lovette.outtamny.schema;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringWriter;
import java.math.RoundingMode;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.Callable;
import java.util.function.Function;

import static io.github.lovette.outtamny.SharedUtils.getPathStem;
import static io.github.lovette.outtamny.SharedUtils.getPathSuffix;
import static io.github.lovette.outtamny.SharedUtils.joinWithParens;

import io.github.lovette.outtamny.App;
import io.github.lovette.outtamny.BaseSubcommand;
import io.github.lovette.outtamny.SharedUtils.Verbosity;
import io.github.lovette.outtamny.UserFacingException;
import io.github.spannm.jackcess.Column;
import io.github.spannm.jackcess.DataType;
import io.github.spannm.jackcess.Database;
import io.github.spannm.jackcess.Index;
import io.github.spannm.jackcess.Row;
import io.github.spannm.jackcess.Table;
import io.github.spannm.jackcess.encrypt.InvalidCredentialsException;
import io.github.spannm.jackcess.impl.IndexImpl;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * Create a set of SQL files with table schemas and data of a Microsoft Money
 * data file
 * importable into SQLite.
 */
@Command(name = "schema", description = "Output SQL that creates Money tables, indexes and data in SQLite.", sortOptions = false)
public class SchemaCommand extends BaseSubcommand implements Callable<Integer> {

    enum OutputScheme {
        combined, // A single file (or stdout)
        split, // Individual file each for schema, indexes, data
        table_combined, // A single file per table
        table, // Individual file each for schema, indexes, data per table
        none
    }

    enum MoneyType {
        numeric, // NUMERIC
        text, // TEXT
        integer // INTEGER ($ * 10,000)
    }

    @ParentCommand
    private App parent; // Parent command reference

    @Option(names = { "-o",
            "--out" }, paramLabel = "DIRECTORY|FILE|-", description = "The name for combined schema file, the directory to write , or '-' for stdout. Defaults to MNYFILE's name and directory.", order = 1)
    private Path outDirOrFilePath;

    @Option(names = {
            "-s",
            "--schema-out" }, description = "Structure schema output format. Valid values: ${COMPLETION-CANDIDATES}", order = 3)
    private OutputScheme schemaOutput = OutputScheme.combined;

    @Option(names = { "-d",
            "--data-out" }, description = "Data output format. Valid values: ${COMPLETION-CANDIDATES}", order = 4)
    private OutputScheme dataOutput = OutputScheme.combined;

    @Option(names = { "-t",
            "--table" }, paramLabel = "name", description = "Limit output to given table. Can be specified multiple times.", order = 5)
    private List<String> filterTables;

    @Option(names = {
            "--money-type" }, description = "Data type to store money values as. Valid values: ${COMPLETION-CANDIDATES}", order = 5)
    private MoneyType moneyType = MoneyType.numeric;

    @Option(names = { "--skip-empty" }, description = "Skip empty tables.", negatable = true, order = 10)
    private boolean skipEmpty = false;

    @Parameters(index = "0", paramLabel = "MNYFILE", description = "Money data file (.mny).")
    private Path mnyPath;

    // foreignKeyReferences()
    public record FKReference(String refs, String refs_by) {
    }

    Path combinedSchemaPath;
    final Map<String, SchemaTableOut> tableOuts = new LinkedHashMap<>();
    final Map<Path, SchemaTableOutWriter> tableOutWriters = new LinkedHashMap<>();

    static final String TABLES_SQL = "tables.sql";
    static final String INDEXES_SQL = "indexes.sql";
    static final String DATA_SQL = "data.sql";
    static final String OUT_STDOUT = "-";

    /**
     * Subcommand entry point.
     */
    @Override
    public Integer call() throws FileNotFoundException, IOException {
        if (mnyPath == null) {
            throw new ParameterException(spec.commandLine(),
                    "Error: MNYFILE path must be specified.");
        }

        mnyPath = mnyPath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(mnyPath)) {
            throw new FileNotFoundException("Error: '" + mnyPath + "': No such file.");
        }

        if (dataOutput == OutputScheme.combined && schemaOutput != OutputScheme.combined) {
            dataOutput = OutputScheme.split;
        }
        if (dataOutput == OutputScheme.table_combined && schemaOutput != OutputScheme.table_combined) {
            dataOutput = OutputScheme.table;
        }

        Path tableOutDirPath;

        if (isOutputStdout()) {
            if (getVerbosityLevel().isMoreThan(Verbosity.ERROR)) {
                String err = "Error: Verbosity level must be quiet when output is to stdout.";
                throw new UserFacingException(err);
            }
            if (!isSingleStreamOut()) {
                String err = "Error: When output is to stdout, '--schema-out' and '--data-out' must each be 'combined' or 'none'.";
                throw new UserFacingException(err);
            }

            combinedSchemaPath = outDirOrFilePath;
            tableOutDirPath = outDirOrFilePath;
        } else {
            if (outDirOrFilePath == null) {
                outDirOrFilePath = mnyPath.getParent();
            } else {
                outDirOrFilePath = outDirOrFilePath.toAbsolutePath().normalize();
            }

            if (Files.isDirectory(outDirOrFilePath)) {
                combinedSchemaPath = outDirOrFilePath.resolve(getPathStem(mnyPath) + ".sql");
            } else if (getPathSuffix(outDirOrFilePath).equalsIgnoreCase("sql")) {
                combinedSchemaPath = outDirOrFilePath; // File will be overwritten if exists!
            } else if (Files.exists(outDirOrFilePath)) {
                String err = "Error: '" + outDirOrFilePath + "': Already exists and will not be overwritten.";
                throw new FileAlreadyExistsException(err);
            } else {
                String err = "Error: '" + outDirOrFilePath + "': No such directory.";
                throw new FileNotFoundException(err);
            }

            tableOutDirPath = combinedSchemaPath.getParent();

            if (!isSingleStreamOut()) {
                // Write table schemas to a subdirectory named after the Money filename.
                tableOutDirPath = getMnyFileOutPath(mnyPath, tableOutDirPath, null);
                Files.createDirectories(tableOutDirPath);
            }

            if (!Files.isDirectory(tableOutDirPath)) {
                throw new FileNotFoundException("Error: '" + tableOutDirPath + "': No such directory.");
            }
        }

        try (Database db = openMnyFile(mnyPath)) {
            List<String> t_names = getMnyTableNames(db, filterTables);

            initTableOuts(db, tableOutDirPath, t_names);

            writeSchemaFiles(db);

        } catch (InvalidCredentialsException e) {
            stdErr().println("Error: Invalid password for the Money data file.");
            return 1;
        }

        return 0;
    }

    /**
     * Determines if there is a single output stream, i.e., the output is a single
     * schema file written to 'combinedSchemaPath' or stdout.
     *
     * @return true if the output is a single stream, false otherwise.
     */
    private boolean isSingleStreamOut() {
        return (dataOutput == OutputScheme.combined
                || (schemaOutput == OutputScheme.combined && dataOutput == OutputScheme.none)
                || (schemaOutput == OutputScheme.none && dataOutput == OutputScheme.split));
    }

    /**
     * Determines if the output is directed to standard output.
     *
     * @return true if the output is to standard output, false otherwise.
     */
    private boolean isOutputStdout() {
        return outDirOrFilePath != null && outDirOrFilePath.toString().equals(OUT_STDOUT);
    }

    /**
     * Initializes the output details for each table based on the schema and data
     * output options and the list of table names.
     *
     * @param db              The database instance.
     * @param tableOutDirPath The path to the schema output directory or
     *                        'OUT_STDOUT'.
     * @param t_names         The list of table names to process.
     * @throws IOException if an I/O error occurs while reading the Money data file.
     */
    private void initTableOuts(Database db, Path tableOutDirPath, List<String> t_names)
            throws IOException {

        // Each output file has it's own TableOutWriter.
        Function<String, SchemaTableOutWriter> getWriterForName = fileName -> {
            Path writerPath = (fileName != OUT_STDOUT) ? tableOutDirPath.resolve(fileName) : Path.of(OUT_STDOUT);
            if (!tableOutWriters.containsKey(writerPath)) {
                tableOutWriters.put(writerPath, new SchemaTableOutWriter(writerPath));
            }
            return tableOutWriters.get(writerPath);
        };

        // Set the stdout writer for all SchemaTableOutWriter objects.
        SchemaTableOutWriter.setStdoutWriter(stdOut());

        for (String t_name : t_names) {
            Table t = db.getTable(t_name);

            if (t.getRowCount() == 0 && skipEmpty) {
                if (getVerbosityLevel().isAtLeast(Verbosity.INFO)) {
                    stdOut().println("Skipping empty table: " + t_name);
                }
                continue; // Skip empty table

            }

            SchemaTableOutWriter schemaWriter = null;
            SchemaTableOutWriter indexWriter = null;
            SchemaTableOutWriter dataWriter = null;

            if (schemaOutput != OutputScheme.none) {
                if (isOutputStdout()) {
                    schemaWriter = getWriterForName.apply(OUT_STDOUT);
                    indexWriter = schemaWriter;
                } else if (schemaOutput == OutputScheme.combined) {
                    String combined_file = isSingleStreamOut() ? combinedSchemaPath.toString() : TABLES_SQL;
                    schemaWriter = getWriterForName.apply(combined_file);
                    indexWriter = schemaWriter;
                } else if (schemaOutput == OutputScheme.split) {
                    schemaWriter = getWriterForName.apply(TABLES_SQL);
                    indexWriter = getWriterForName.apply(INDEXES_SQL);
                } else if (schemaOutput == OutputScheme.table_combined) {
                    schemaWriter = getWriterForName.apply(t_name + ".sql");
                    indexWriter = schemaWriter;
                } else if (schemaOutput == OutputScheme.table) {
                    schemaWriter = getWriterForName.apply(t_name + "_" + TABLES_SQL);
                    indexWriter = getWriterForName.apply(t_name + "_" + INDEXES_SQL);
                } else {
                    String err = "Invalid value for option '--schema-out': '" + schemaOutput + "'";
                    throw new ParameterException(spec.commandLine(), err);
                }
            }

            if (dataOutput != OutputScheme.none) {
                if (isOutputStdout()) {
                    dataWriter = getWriterForName.apply(OUT_STDOUT);
                } else if (isSingleStreamOut()) {
                    dataWriter = getWriterForName.apply(combinedSchemaPath.toString());
                } else if (dataOutput == OutputScheme.split) {
                    dataWriter = getWriterForName.apply(DATA_SQL);
                } else if (dataOutput == OutputScheme.table_combined) {
                    dataWriter = getWriterForName.apply(t_name + ".sql");
                } else if (dataOutput == OutputScheme.table) {
                    dataWriter = getWriterForName.apply(t_name + "_" + DATA_SQL);
                } else {
                    String err = "Invalid value for option '--data-out': '" + dataOutput + "'";
                    throw new ParameterException(spec.commandLine(), err);
                }
            }

            if (schemaWriter == null && dataWriter == null) {
                throw new UserFacingException("Options '--schema-out' and '--data-out' cannot both be set to 'none'.");
            }

            SchemaTableOut to = new SchemaTableOut(t, schemaWriter, indexWriter, dataWriter);

            tableOuts.put(t_name, to);
        }
    }

    /**
     * Writes the schema of the given database to files.
     *
     * @param db the database to dump the schema from
     * @throws IOException if an I/O error occurs while writing the files
     * @throws IOException if an I/O error occurs while reading the Money data file.
     */
    private void writeSchemaFiles(Database db)
            throws IOException {

        if (getVerbosityLevel().isAtLeast(Verbosity.DEBUG)) {
            LinkedHashSet<Path> writerPaths = new LinkedHashSet<>();
            for (SchemaTableOutWriter tow : tableOutWriters.values()) {
                writerPaths.add(tow.getWriterPath());
            }

            for (Path p : writerPaths) {
                stdOut().println("Writing file: '" + p + "'");
            }
        }

        SchemaTableOutWriter combinedWriter = isSingleStreamOut() ? tableOutWriters.values().iterator().next() : null;

        if (combinedWriter != null) {
            combinedWriter.writeHeader();
        }

        for (SchemaTableOut to : tableOuts.values()) {
            if (to.schemaWriter == null) {
                break; // Not writing table schemas
            }

            String s = writeCreateTable(to.table, to.columns);
            to.schemaWriter.writeCreateTable(to, s);
        }

        for (SchemaTableOutWriter tow : tableOutWriters.values()) {
            tow.commitTransaction();
        }

        for (SchemaTableOut to : tableOuts.values()) {
            if (to.dataWriter == null) {
                break; // Not writing data
            }

            int n_rows = to.table.getRowCount();

            if (getVerbosityLevel().isAtLeast(Verbosity.INFO)) {
                stdOut().println(String.format("%-35s rows=%d", to.table.getName(), n_rows));
            }

            if (n_rows == 0) {
                continue;
            }

            String s = writeInsertTable(to.table, to.columns);
            to.dataWriter.writeInsertTable(to, s);
        }

        for (SchemaTableOutWriter tow : tableOutWriters.values()) {
            tow.commitTransaction();
        }

        for (SchemaTableOut to : tableOuts.values()) {
            if (to.indexWriter == null) {
                break; // Not writing indexes
            }

            String s = writeCreateIndexes(to.table);
            to.indexWriter.writeCreateIndex(to, s);
        }

        for (SchemaTableOutWriter tow : tableOutWriters.values()) {
            tow.forceCommitTransaction();
        }

        if (combinedWriter != null) {
            combinedWriter.writeFooter();
        } else {
            writeCombinedSchema();
        }
    }

    /**
     * Writes the combined schema SQL file by including all individual table schema
     * files in the correct order.
     */
    private void writeCombinedSchema() throws IOException {
        SchemaTableOutWriter combinedWriter = new SchemaTableOutWriter(combinedSchemaPath);
        Path schemaDir = combinedSchemaPath.getParent();
        LinkedHashSet<Path> schemaPaths = new LinkedHashSet<>();
        LinkedHashSet<Path> indexesPaths = new LinkedHashSet<>();
        LinkedHashSet<Path> dataPaths = new LinkedHashSet<>();

        for (SchemaTableOutWriter tow : tableOutWriters.values()) {
            combinedWriter.addSubWriter(tow);
        }

        for (SchemaTableOut to : tableOuts.values()) {
            if (to.schemaWritten) {
                Path relPath = to.schemaWriter.relativizeWriterPath(schemaDir);
                schemaPaths.add(relPath);
            }
            if (to.indexWritten) {
                Path relPath = to.indexWriter.relativizeWriterPath(schemaDir);
                indexesPaths.add(relPath);
            }
            if (to.dataWritten) {
                Path relPath = to.dataWriter.relativizeWriterPath(schemaDir);
                dataPaths.add(relPath);
            }
        }

        combinedWriter.writeHeader();
        combinedWriter.writePathsBlock(schemaPaths);
        combinedWriter.writePathsBlock(dataPaths);
        combinedWriter.writePathsBlock(indexesPaths);
        combinedWriter.writeFooter();
    }

    /**
     * Writes the SQL CREATE TABLE statement for the given table and its columns.
     *
     * @param t    the table to generate the CREATE TABLE statement for
     * @param cols the list of columns to include in the CREATE TABLE statement
     * @return the SQL CREATE TABLE statement as a string
     * @throws IOException if an I/O error occurs while reading the Money data file.
     */
    String writeCreateTable(Table t, List<? extends Column> cols) throws IOException {

        StringWriter sw = new StringWriter();

        Map<String, List<Index>> pk_ix_map = new HashMap<>(); // col -> list of primary key indexes
        Map<String, List<Index>> uk_ix_map = new HashMap<>(); // col -> list of unique key indexes
        Map<String, List<Index>> fk_ix_map = new HashMap<>(); // col -> list of foreign key indexes
        Map<String, List<Index>> other_ix_map = new HashMap<>(); // col -> list of other indexes

        for (Index ix : t.getIndexes()) {
            if (ix.isForeignKey() && ix.isPrimaryKey()) {
                throw new IllegalStateException("Expected foreign key to not be a primary key.");
            }
            if (ix.isPrimaryKey() && !ix.isUnique()) {
                throw new IllegalStateException("Expected primary key index to also be unique.");
            }

            Map<String, List<Index>> add_ix_map = other_ix_map;

            if (ix.isForeignKey()) {
                add_ix_map = fk_ix_map;
            } else if (ix.isPrimaryKey()) {
                add_ix_map = pk_ix_map;
            } else if (ix.isUnique()) {
                add_ix_map = uk_ix_map;
            }

            for (Index.Column c : ix.getColumns()) {
                add_ix_map.computeIfAbsent(c.getColumn().getName(), k -> new ArrayList<>()).add(ix);
            }
        }

        String name = t.getName();
        Index t_pk_ix = t.getPrimaryKeyIndex();
        boolean t_pk_is_composite = (t_pk_ix.getColumnCount() > 1);
        StringJoiner sj_table = new StringJoiner(",\n", "", ");\n");

        sw.write("CREATE TABLE " + sqlNameQ(name) + " (\n");

        for (int i = 0; i < cols.size(); i++) {
            Column c = cols.get(i);
            String c_name = c.getName();
            List<Index> fk_ixs = fk_ix_map.get(c_name);
            List<Index> pk_ixs = pk_ix_map.get(c_name);
            List<Index> uk_ixs = uk_ix_map.get(c_name);
            StringJoiner sj_col = new StringJoiner(" ");

            // None of the columns in Money database have any properties...
            // PropertyMap c_props = c.getProperties();
            // if (!c_props.isEmpty()) {
            // nullable = !((Boolean) c_props.getValue(PropertyMap.REQUIRED_PROP,
            // Boolean.FALSE));
            // }
            Boolean c_nullable = null;

            FKReference fk_ref = foreignKeyReferences(t, c, fk_ixs);
            String fk_refs = fk_ref.refs();
            String fk_refs_by = fk_ref.refs_by();

            sj_col.add(" ");
            sj_col.add(sqlNameQ(c_name));
            sj_col.add(sqliteDataType(c.getType()));

            if (pk_ixs != null) {
                if (pk_ixs.size() > 1) {
                    throw new IllegalStateException("Column " + c_name + " has more than one primary key index.");
                }

                // In SQLite, a foreign key must reference a column in the parent table that is
                // explicitly marked as a PRIMARY KEY or has a UNIQUE constraint. I've seen
                // child columns that are marked as both a foreign key and a primary key. This
                // seems to only make sense if tables are chained, which TRN_INVOICE(htrn) seems
                // to be:
                // PMT(htrnInvoice) > TRN_INVOICE(htrn) > TRN(htrn)

                if (!t_pk_is_composite) {
                    sj_col.add("PRIMARY KEY");
                }
                c_nullable = false; // explicitly mark primary key columns as not nullable
            }

            if (uk_ixs != null) {
                for (Index ix : uk_ixs) {
                    if (ix.getColumnCount() == 1 && ix.getColumns().get(0).getName().equals(c_name)) {
                        // Mark column UNIQUE in table def instead of a separate CREATE UNIQUE INDEX.
                        sj_col.add("UNIQUE");
                    }
                }
            }

            if (c_nullable == null) {
                // Guess based on data we see...
                c_nullable = columnHasNulls(t, c);
            }
            if (!c_nullable) {
                sj_col.add("NOT NULL");
            }

            if (fk_refs != null) {
                sj_col.add("REFERENCES " + fk_refs);
            }

            if (fk_refs_by != null) {
                sj_col.add("/* REFERENCED BY " + fk_refs_by + " */");
            }

            sj_table.add(sj_col.toString());
        }

        if (t_pk_is_composite) {
            // Composite primary keys are defined at the table level, not the column level.
            String[] pk_cols = t_pk_ix.getColumns().stream().map(c -> sqlNameQ(c.getName())).toArray(String[]::new);
            sj_table.add("  PRIMARY KEY " + joinWithParens(pk_cols));
        }

        sw.write(sj_table.toString());

        return sw.toString();
    }

    /**
     * Writes SQL INSERT INTO statements for the given table and its columns.
     *
     * @param t    the table to generate the INSERT INTO statement for
     * @param cols the list of columns to include in the INSERT INTO statement
     * @return the SQL INSERT INTO statement as a string
     * @throws IOException if an I/O error occurs while reading the Money data file.
     */
    String writeInsertTable(Table t, List<? extends Column> cols) throws IOException {

        StringWriter sw = new StringWriter();

        int n_row = 0;
        final int batch_size = 500;
        String name = t.getName();

        for (Row r : t) {
            if (n_row % batch_size == 0) {
                if (n_row > 0) {
                    sw.write(";\n");
                }

                sw.write("INSERT INTO " + sqlNameQ(name) + " VALUES\n");
            } else {
                sw.write(",\n");
            }

            String[] values = cols.stream().map(c -> sqlValue(r.get(c.getName()))).toArray(String[]::new);

            sw.write(joinWithParens(values));

            n_row++;
        }

        if (n_row > 0) {
            sw.write(";\n");
        }

        return sw.toString();
    }

    /**
     * Writes the CREATE INDEX statements for the given table and its columns.
     *
     * @param t the table to generate INSERT statements for
     * @return the SQL CREATE INDEX statements as a string
     * @throws IOException if an I/O error occurs while reading the Money data file.
     */
    String writeCreateIndexes(Table t) throws IOException {

        String name = t.getName();
        List<String> indexes = new ArrayList<>();

        for (Index ix : t.getIndexes()) {
            if (ix.isForeignKey() || ix.isPrimaryKey()) {
                continue;
            }

            String ix_name = "idx_" + name + "_" + ix.getName();
            String[] ix_cols = ix.getColumns().stream().map(c -> sqlNameQ(c.getName())).toArray(String[]::new);
            StringJoiner sj_create = new StringJoiner(" ", "", ";\n");

            if (ix.isUnique() && ix.getColumnCount() == 1) {
                // We used an inline constraint in the CREATE TABLE statement.
                continue;
            }

            sj_create.add("CREATE");
            if (ix.isUnique()) {
                sj_create.add("UNIQUE");
            }
            sj_create.add("INDEX");
            sj_create.add(sqlNameU(ix_name));
            sj_create.add("ON");
            sj_create.add(sqlNameQ(name));
            sj_create.add(joinWithParens(ix_cols));

            indexes.add(sj_create.toString());
        }

        Collections.sort(indexes); // For asthetics.

        StringWriter sw = new StringWriter();
        for (String ix : indexes) {
            sw.write(ix);
        }

        return sw.toString();
    }

    /**
     * Determine from a foreign key index reference whether it is a child table
     * REFERENCES or parent table REFERENCED BY relationship.
     *
     * @param t      the table containing the foreign key indexes
     * @param c      the column in the table
     * @param fk_ixs the list of foreign key indexes on the table
     * @return the foreign key reference clause as an FKReferences object, or null
     *         if no foreign
     *         key indexes are provided
     * @throws IOException if an I/O error occurs while reading the Money data file.
     */
    static FKReference foreignKeyReferences(Table t, Column t_c, List<Index> fk_ixs) throws IOException {

        if (fk_ixs == null) {
            return new FKReference(null, null);
        }

        List<String> fk_refs = new ArrayList<>();
        List<String> fk_refs_by = new ArrayList<>();

        for (Index fk_ix : fk_ixs) {
            if (!(fk_ix instanceof IndexImpl)) {
                throw new IllegalStateException("Expected 'fk_ix' to be an instance of 'IndexImpl'");
            }

            IndexImpl childIndex = (IndexImpl) fk_ix;
            Index parentIndex = childIndex.getReferencedIndex();
            String childTable = childIndex.getTable().getName();
            String parentTable = parentIndex.getTable().getName();

            // When Microsoft Access creates a foreign key relationship between tables, it
            // attaches it to both tables and the only thing that differentiates a
            // REFERENCES from a REFERENCED BY relationship is the hidden nature of the
            // index. Jackcess exposes these underlying engine-level or relationship
            // indexes, which often carry internal or prefixed names like .rC rather than
            // friendly user names.
            boolean ix_hidden = fk_ix.getName().startsWith(("."));

            if (!childTable.equals(t.getName())) {
                throw new IllegalStateException("Expected child table to match current table");
            }
            if (childIndex.isPrimaryKey() || parentIndex.isPrimaryKey()) {
                throw new IllegalStateException("Foreign key indexes not expected to also be primary key");
            }

            String[] fk_cols = parentIndex.getColumns().stream().map(c -> sqlNameQ(c.getName())).toArray(String[]::new);

            // if (false) {
            // String[] fk_cols_by = childIndex.getColumns().stream().map(c ->
            // ident(c.getName()))
            // .toArray(String[]::new);
            // stdOut().println("--- " + fk_ix.getName() + ": " + t.getName() + "(" +
            // t_c.getName() + ")");
            // stdOut().println(String.format("C: %-15s | %-10s | %-10s | %s",
            // childTable,
            // childIndex.isPrimaryKey() ? "PRIMARY" : "",
            // childIndex.isUnique() ? "UNIQUE" : "",
            // Arrays.toString(fk_cols_by)));
            // stdOut().println(String.format("P: %-15s | %-10s | %-10s | %s",
            // parentTable,
            // parentIndex.isPrimaryKey() ? "PRIMARY" : "",
            // parentIndex.isUnique() ? "UNIQUE" : "",
            // Arrays.toString(fk_cols)));
            // }

            if (ix_hidden) {
                fk_refs_by.add(parentTable + joinWithParens(fk_cols)); // parent table REFERENCED BY child
            } else {
                fk_refs.add(parentTable + joinWithParens(fk_cols)); // child table REFERENCES parent
            }
        }

        Collections.sort(fk_refs);
        Collections.sort(fk_refs_by);

        StringJoiner sj_refs = new StringJoiner(", ");
        for (String ref : fk_refs) {
            sj_refs.add(ref);
        }

        StringJoiner sj_refs_by = new StringJoiner(", ");
        for (String ref : fk_refs_by) {
            sj_refs_by.add(ref);
        }

        String refs = (sj_refs.length() > 0) ? sj_refs.toString() : null;
        String refs_by = (sj_refs_by.length() > 0) ? sj_refs_by.toString() : null;

        return new FKReference(refs, refs_by);
    }

    /**
     * Returns the SQLite data type corresponding to the given Jackcess DataType.
     *
     * @param t the Jackcess DataType to convert
     * @return the SQLite data type as a string
     */
    @Override
    protected String sqliteDataType(DataType t) {
        switch (t) {
            case MONEY:
            case NUMERIC:
                if (moneyType == MoneyType.text)
                    return "TEXT";
                if (moneyType == MoneyType.integer)
                    return "INTEGER";
                return "NUMERIC";
            default:
                return super.sqliteDataType(t);
        }
    }

    /**
     * Converts a Java object to its SQL value such as "null > NULL" or "true > 1".
     *
     * @param obj the value to convert
     * @return the SQL value
     */
    @Override
    protected String sqlValue(Object obj) {
        if (obj instanceof java.math.BigDecimal bd && moneyType == MoneyType.integer) {
            return bd.setScale(4, RoundingMode.HALF_UP).movePointRight(4).toBigInteger().toString();
        }
        return super.sqlValue(obj);
    }

    boolean columnHasNulls(Table t, Column c) {
        String c_name = c.getName();

        try {
            for (Row row : t) {
                Object v = row.get(c_name);
                if (v == null) {
                    return true;
                }
            }
        } catch (java.time.DateTimeException e) {
            // byte[] rawBytes = row.getBytes(c_name);
            // String s = row.getString(c_name);
            // if (s == null) {
            stdErr().println(t.getName() + ": DateTimeException for column: " + c_name);
            // }
        }

        return false;
    }
}
