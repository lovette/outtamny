package io.github.lovette.outtamny.schema;

import static io.github.lovette.outtamny.SharedUtils.getPathStem;
import static io.github.lovette.outtamny.SharedUtils.getPathSuffix;
import static io.github.lovette.outtamny.SharedUtils.joinWithParens;

import io.github.lovette.outtamny.BaseSubcommand;
import io.github.lovette.outtamny.CommandUsageMixin;
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringWriter;
import java.math.RoundingMode;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.Callable;
import java.util.function.Function;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;

/**
 * Create a set of SQL files with table schemas and data of a Microsoft Money data file importable
 * into SQLite.
 */
@Command(name = "schema", description = "Output SQL that creates Money tables, indexes and data in SQLite.")
public class SchemaCommand extends BaseSubcommand implements Callable<Integer> {

    enum OutputScheme {
        COMBINED, // A single file (or stdout)
        SPLIT, // Individual file each for schema, indexes, data
        TABLE_COMBINED, // A single file per table
        TABLE, // Individual file each for schema, indexes, data per table
        NONE
    }

    enum MoneyType {
        NUMERIC, // NUMERIC
        TEXT, // TEXT
        INTEGER // INTEGER ($ * 10,000)
    }

    @Mixin
    CommandUsageMixin usageMixin;

    @Option(
            names = {"-o", "--out"},
            paramLabel = "DIRECTORY|FILE|-",
            description =
                    "The name for combined schema file, the directory to write , or '-' for stdout. Defaults to MNYFILE's name and directory.",
            order = 1)
    private Path outDirOrFilePath;

    @Option(
            names = {"-s", "--schema-out"},
            paramLabel = "format",
            description = "Structure schema output format. Valid values: ${COMPLETION-CANDIDATES}",
            completionCandidates = FormatOutputScheme.class,
            order = 3)
    private OutputScheme schemaOutput = OutputScheme.COMBINED;

    @Option(
            names = {"-d", "--data-out"},
            paramLabel = "format",
            description = "Data output format. Valid values: ${COMPLETION-CANDIDATES}",
            completionCandidates = FormatOutputScheme.class,
            order = 4)
    private OutputScheme dataOutput = OutputScheme.COMBINED;

    @Option(
            names = {"-t", "--table"},
            paramLabel = "name",
            description = "Limit output to given table. Can be specified multiple times.",
            order = 5)
    private List<String> filterTables;

    @Option(
            names = {"--money-type"},
            paramLabel = "type",
            description = "Data type to store money values as. Valid values: ${COMPLETION-CANDIDATES}",
            completionCandidates = FormatMoneyType.class,
            order = 9)
    private MoneyType moneyType = MoneyType.NUMERIC;

    @Option(
            names = {"--skip-empty"},
            description = "Skip empty tables.",
            negatable = true,
            order = 10)
    private boolean skipEmpty = false;

    @Parameters(index = "0", paramLabel = "MNYFILE", description = "Money data file (.mny).")
    private Path mnyPath;

    // foreignKeyReferences()
    public record FKReference(String refs, String refs_by) {}

    Path combinedSchemaPath;
    final Map<String, SchemaTableOut> tableOuts = new LinkedHashMap<>();
    final Map<Path, SchemaTableOutWriter> tableOutWriters = new LinkedHashMap<>();

    static final String TABLES_SQL = "tables.sql";
    static final String INDEXES_SQL = "indexes.sql";
    static final String DATA_SQL = "data.sql";
    static final String OUT_STDOUT = "-";

    static class FormatOutputScheme extends LowercaseEnumCandidates<OutputScheme> {
        FormatOutputScheme() {
            super(OutputScheme.class);
        }
    }

    static class FormatMoneyType extends LowercaseEnumCandidates<MoneyType> {
        FormatMoneyType() {
            super(MoneyType.class);
        }
    }

    /** Subcommand entry point. */
    @Override
    public Integer call() throws IOException {
        if (mnyPath == null) {
            throw new ParameterException(spec.commandLine(), "Error: MNYFILE path must be specified.");
        }

        mnyPath = mnyPath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(mnyPath)) {
            throw new FileNotFoundException("Error: '" + mnyPath + "': No such file.");
        }

        if (dataOutput == OutputScheme.COMBINED && schemaOutput != OutputScheme.COMBINED) {
            dataOutput = OutputScheme.SPLIT;
        }
        if (dataOutput == OutputScheme.TABLE_COMBINED && schemaOutput != OutputScheme.TABLE_COMBINED) {
            dataOutput = OutputScheme.TABLE;
        }

        Path tableOutDirPath;

        if (isOutputStdout()) {
            if (getVerbosityLevel().isMoreThan(Verbosity.ERROR)) {
                String err = "Error: Verbosity level must be quiet when output is to stdout.";
                throw new UserFacingException(err);
            }
            if (!isSingleStreamOut()) {
                String err =
                        "Error: When output is to stdout, '--schema-out' and '--data-out' must each be 'combined' or 'none'.";
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
            List<String> tableNames = getMnyTableNames(db, filterTables);

            initTableOuts(db, tableOutDirPath, tableNames);

            writeSchemaFiles();

        } catch (InvalidCredentialsException _) {
            stdErr().println("Error: Invalid password for the Money data file.");
            return 1;
        }

        return 0;
    }

    /**
     * Determines if there is a single output stream, i.e., the output is a single schema file
     * written to 'combinedSchemaPath' or stdout.
     *
     * @return true if the output is a single stream, false otherwise.
     */
    private boolean isSingleStreamOut() {
        return (dataOutput == OutputScheme.COMBINED
                || (schemaOutput == OutputScheme.COMBINED && dataOutput == OutputScheme.NONE)
                || (schemaOutput == OutputScheme.NONE && dataOutput == OutputScheme.SPLIT));
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
     * Initializes the output details for each table based on the schema and data output options and
     * the list of table names.
     *
     * @param db The database instance.
     * @param tableOutDirPath The path to the schema output directory or 'OUT_STDOUT'.
     * @param tableNames The list of table names to process.
     * @throws IOException if an I/O error occurs while reading the Money data file.
     */
    private void initTableOuts(Database db, Path tableOutDirPath, List<String> tableNames) throws IOException {

        // Each output file has it's own TableOutWriter.
        Function<String, SchemaTableOutWriter> getWriterForName = fileName -> {
            Path writerPath =
                    (Objects.equals(fileName, OUT_STDOUT)) ? Path.of(OUT_STDOUT) : tableOutDirPath.resolve(fileName);
            if (!tableOutWriters.containsKey(writerPath)) {
                tableOutWriters.put(writerPath, new SchemaTableOutWriter(writerPath));
            }
            return tableOutWriters.get(writerPath);
        };

        // Set the stdout writer for all SchemaTableOutWriter objects.
        SchemaTableOutWriter.setStdoutWriter(stdOut());

        for (String tableName : tableNames) {
            Table t = db.getTable(tableName);

            if (t.getRowCount() == 0 && skipEmpty) {
                if (getVerbosityLevel().isAtLeast(Verbosity.INFO)) {
                    stdOut().println("Skipping empty table: " + tableName);
                }
                continue; // Skip empty table
            }

            SchemaTableOutWriter schemaWriter = null;
            SchemaTableOutWriter indexWriter = null;
            SchemaTableOutWriter dataWriter = null;

            if (schemaOutput != OutputScheme.NONE) {
                if (isOutputStdout()) {
                    schemaWriter = getWriterForName.apply(OUT_STDOUT);
                    indexWriter = schemaWriter;
                } else if (schemaOutput == OutputScheme.COMBINED) {
                    String combinedFileName = isSingleStreamOut() ? combinedSchemaPath.toString() : TABLES_SQL;
                    schemaWriter = getWriterForName.apply(combinedFileName);
                    indexWriter = schemaWriter;
                } else if (schemaOutput == OutputScheme.SPLIT) {
                    schemaWriter = getWriterForName.apply(TABLES_SQL);
                    indexWriter = getWriterForName.apply(INDEXES_SQL);
                } else if (schemaOutput == OutputScheme.TABLE_COMBINED) {
                    schemaWriter = getWriterForName.apply(tableName + ".sql");
                    indexWriter = schemaWriter;
                } else if (schemaOutput == OutputScheme.TABLE) {
                    schemaWriter = getWriterForName.apply(tableName + "_" + TABLES_SQL);
                    indexWriter = getWriterForName.apply(tableName + "_" + INDEXES_SQL);
                } else {
                    String err = "Invalid value for option '--schema-out': '" + schemaOutput + "'";
                    throw new ParameterException(spec.commandLine(), err);
                }
            }

            if (dataOutput != OutputScheme.NONE) {
                if (isOutputStdout()) {
                    dataWriter = getWriterForName.apply(OUT_STDOUT);
                } else if (isSingleStreamOut()) {
                    dataWriter = getWriterForName.apply(combinedSchemaPath.toString());
                } else if (dataOutput == OutputScheme.SPLIT) {
                    dataWriter = getWriterForName.apply(DATA_SQL);
                } else if (dataOutput == OutputScheme.TABLE_COMBINED) {
                    dataWriter = getWriterForName.apply(tableName + ".sql");
                } else if (dataOutput == OutputScheme.TABLE) {
                    dataWriter = getWriterForName.apply(tableName + "_" + DATA_SQL);
                } else {
                    String err = "Invalid value for option '--data-out': '" + dataOutput + "'";
                    throw new ParameterException(spec.commandLine(), err);
                }
            }

            if (schemaWriter == null && dataWriter == null) {
                throw new UserFacingException("Options '--schema-out' and '--data-out' cannot both be set to 'none'.");
            }

            SchemaTableOut to = new SchemaTableOut(t, schemaWriter, indexWriter, dataWriter);

            tableOuts.put(tableName, to);
        }
    }

    /**
     * Writes the schema of the given database to files.
     *
     * @throws IOException if an I/O error occurs while writing the files
     * @throws IOException if an I/O error occurs while reading the Money data file.
     */
    private void writeSchemaFiles() throws IOException {

        if (getVerbosityLevel().isAtLeast(Verbosity.DEBUG)) {
            LinkedHashSet<Path> writerPaths = new LinkedHashSet<>();
            for (SchemaTableOutWriter tow : tableOutWriters.values()) {
                writerPaths.add(tow.getWriterPath());
            }

            for (Path p : writerPaths) {
                stdOut().println("Writing file: '" + p + "'");
            }
        }

        SchemaTableOutWriter combinedWriter =
                isSingleStreamOut() ? tableOutWriters.values().iterator().next() : null;

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

            int nRows = to.table.getRowCount();

            if (getVerbosityLevel().isAtLeast(Verbosity.INFO)) {
                stdOut().printf("%-35s rows=%d%n", to.table.getName(), nRows);
            }

            if (nRows == 0) {
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
     * Writes the combined schema SQL file by including all individual table schema files in the
     * correct order.
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
     * @param t the table to generate the CREATE TABLE statement for
     * @param cols the list of columns to include in the CREATE TABLE statement
     * @return the SQL CREATE TABLE statement as a string
     * @throws IOException if an I/O error occurs while reading the Money data file.
     */
    String writeCreateTable(Table t, List<? extends Column> cols) throws IOException {

        StringWriter sw = new StringWriter();

        Map<String, List<Index>> pkIxMap = new HashMap<>(); // col -> list of primary key indexes
        Map<String, List<Index>> ukIxMap = new HashMap<>(); // col -> list of unique key indexes
        Map<String, List<Index>> fkIxMap = new HashMap<>(); // col -> list of foreign key indexes
        Map<String, List<Index>> otherIxMap = new HashMap<>(); // col -> list of other indexes

        for (Index ix : t.getIndexes()) {
            if (ix.isForeignKey() && ix.isPrimaryKey()) {
                throw new IllegalStateException("Expected foreign key to not be a primary key.");
            }
            if (ix.isPrimaryKey() && !ix.isUnique()) {
                throw new IllegalStateException("Expected primary key index to also be unique.");
            }

            Map<String, List<Index>> addIxMap = otherIxMap;

            if (ix.isForeignKey()) {
                addIxMap = fkIxMap;
            } else if (ix.isPrimaryKey()) {
                addIxMap = pkIxMap;
            } else if (ix.isUnique()) {
                addIxMap = ukIxMap;
            }

            for (Index.Column c : ix.getColumns()) {
                addIxMap.computeIfAbsent(c.getColumn().getName(), k -> new ArrayList<>())
                        .add(ix);
            }
        }

        String name = t.getName();
        Index tablePkIx = t.getPrimaryKeyIndex();
        boolean tablePkIsComposite = (tablePkIx.getColumnCount() > 1);
        StringJoiner sjTable = new StringJoiner(",\n", "", ");\n");

        sw.write("CREATE TABLE " + sqlNameQ(name) + " (\n");

        for (int i = 0; i < cols.size(); i++) {
            Column c = cols.get(i);
            String colName = c.getName();
            List<Index> fkIxs = fkIxMap.get(colName);
            List<Index> pkIxs = pkIxMap.get(colName);
            List<Index> ukIxs = ukIxMap.get(colName);
            StringJoiner sjCol = new StringJoiner(" ");
            Boolean colNullable = null;
            FKReference fkRef = foreignKeyReferences(t, fkIxs);
            String fkRefs = fkRef.refs();
            String fkRefsBy = fkRef.refs_by();

            sjCol.add(" ");
            sjCol.add(sqlNameQ(colName));
            sjCol.add(sqliteDataType(c.getType()));

            if (pkIxs != null) {
                if (pkIxs.size() > 1) {
                    throw new IllegalStateException("Column " + colName + " has more than one primary key index.");
                }

                // In SQLite, a foreign key must reference a column in the parent table that is
                // explicitly marked as a PRIMARY KEY or has a UNIQUE constraint. I've seen
                // child columns that are marked as both a foreign key and a primary key. This
                // seems to only make sense if tables are chained, which TRN_INVOICE(htrn) seems
                // to be:
                // PMT(htrnInvoice) > TRN_INVOICE(htrn) > TRN(htrn)

                if (!tablePkIsComposite) {
                    sjCol.add("PRIMARY KEY");
                }
                colNullable = false; // explicitly mark primary key columns as not nullable
            }

            if (ukIxs != null) {
                for (Index ix : ukIxs) {
                    if (ix.getColumnCount() == 1
                            && ix.getColumns().get(0).getName().equals(colName)) {
                        // Mark column UNIQUE in table def instead of a separate CREATE UNIQUE
                        // INDEX.
                        sjCol.add("UNIQUE");
                    }
                }
            }

            if (colNullable == null) {
                // Guess based on data we see...
                colNullable = columnHasNulls(t, c);
            }
            if (Boolean.FALSE.equals(colNullable)) {
                sjCol.add("NOT NULL");
            }

            if (fkRefs != null) {
                sjCol.add("REFERENCES " + fkRefs);
            }

            if (fkRefsBy != null) {
                sjCol.add("/* REFERENCED BY " + fkRefsBy + " */");
            }

            sjTable.add(sjCol.toString());
        }

        if (tablePkIsComposite) {
            // Composite primary keys are defined at the table level, not the column level.
            String[] pkCols = tablePkIx.getColumns().stream()
                    .map(c -> sqlNameQ(c.getName()))
                    .toArray(String[]::new);
            sjTable.add("  PRIMARY KEY " + joinWithParens(pkCols));
        }

        sw.write(sjTable.toString());

        return sw.toString();
    }

    /**
     * Writes SQL INSERT INTO statements for the given table and its columns.
     *
     * @param t the table to generate the INSERT INTO statement for
     * @param cols the list of columns to include in the INSERT INTO statement
     * @return the SQL INSERT INTO statement as a string
     * @throws IOException if an I/O error occurs while reading the Money data file.
     */
    String writeInsertTable(Table t, List<? extends Column> cols) throws IOException {

        StringWriter sw = new StringWriter();

        int nRow = 0;
        final int batch_size = 500;
        String name = t.getName();

        for (Row r : t) {
            if (nRow % batch_size == 0) {
                if (nRow > 0) {
                    sw.write(";\n");
                }

                sw.write("INSERT INTO " + sqlNameQ(name) + " VALUES\n");
            } else {
                sw.write(",\n");
            }

            String[] values =
                    cols.stream().map(c -> sqlValue(r.get(c.getName()))).toArray(String[]::new);

            sw.write(joinWithParens(values));

            nRow++;
        }

        if (nRow > 0) {
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

            String ixName = "idx_" + name + "_" + ix.getName();
            String[] ixCols =
                    ix.getColumns().stream().map(c -> sqlNameQ(c.getName())).toArray(String[]::new);
            StringJoiner sjCreate = new StringJoiner(" ", "", ";\n");

            if (ix.isUnique() && ix.getColumnCount() == 1) {
                // We used an inline constraint in the CREATE TABLE statement.
                continue;
            }

            sjCreate.add("CREATE");
            if (ix.isUnique()) {
                sjCreate.add("UNIQUE");
            }
            sjCreate.add("INDEX");
            sjCreate.add(sqlNameU(ixName));
            sjCreate.add("ON");
            sjCreate.add(sqlNameQ(name));
            sjCreate.add(joinWithParens(ixCols));

            indexes.add(sjCreate.toString());
        }

        indexes.sort(null); // For asthetics.

        StringWriter sw = new StringWriter();
        for (String ix : indexes) {
            sw.write(ix);
        }

        return sw.toString();
    }

    /**
     * Determine from a foreign key index reference whether it is a child table REFERENCES or parent
     * table REFERENCED BY relationship.
     *
     * @param t the table containing the foreign key indexes
     * @param fkIxs the list of foreign key indexes on the table
     * @return the foreign key reference clause as an FKReferences object, or null if no foreign key
     *     indexes are provided
     * @throws IOException if an I/O error occurs while reading the Money data file.
     */
    static FKReference foreignKeyReferences(Table t, List<Index> fkIxs) throws IOException {

        if (fkIxs == null) {
            return new FKReference(null, null);
        }

        List<String> fkRefs = new ArrayList<>();
        List<String> fkRefsBy = new ArrayList<>();

        for (Index fk_ix : fkIxs) {
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
            boolean ixHidden = fk_ix.getName().startsWith(("."));

            if (!childTable.equals(t.getName())) {
                throw new IllegalStateException("Expected child table to match current table");
            }
            if (childIndex.isPrimaryKey() || parentIndex.isPrimaryKey()) {
                throw new IllegalStateException("Foreign key indexes not expected to also be primary key");
            }

            String[] fkCols = parentIndex.getColumns().stream()
                    .map(c -> sqlNameQ(c.getName()))
                    .toArray(String[]::new);

            if (ixHidden) {
                fkRefsBy.add(parentTable + joinWithParens(fkCols)); // parent table REFERENCED BY child
            } else {
                fkRefs.add(parentTable + joinWithParens(fkCols)); // child table REFERENCES parent
            }
        }

        fkRefs.sort(null);
        fkRefsBy.sort(null);

        StringJoiner sjRefs = new StringJoiner(", ");
        for (String ref : fkRefs) {
            sjRefs.add(ref);
        }

        StringJoiner sjRefsBy = new StringJoiner(", ");
        for (String ref : fkRefsBy) {
            sjRefsBy.add(ref);
        }

        String refs = (sjRefs.length() > 0) ? sjRefs.toString() : null;
        String refsBy = (sjRefsBy.length() > 0) ? sjRefsBy.toString() : null;

        return new FKReference(refs, refsBy);
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
            case MONEY, NUMERIC:
                if (moneyType == MoneyType.TEXT) return "TEXT";
                if (moneyType == MoneyType.INTEGER) return "INTEGER";
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
        if (obj instanceof java.math.BigDecimal bd && moneyType == MoneyType.INTEGER) {
            return bd.setScale(4, RoundingMode.HALF_UP)
                    .movePointRight(4)
                    .toBigInteger()
                    .toString();
        }
        return super.sqlValue(obj);
    }

    boolean columnHasNulls(Table t, Column c) {
        String colName = c.getName();

        try {
            for (Row row : t) {
                Object v = row.get(colName);
                if (v == null) {
                    return true;
                }
            }
        } catch (java.time.DateTimeException _) {
            stdErr().println(t.getName() + ": DateTimeException for column: " + colName);
        }

        return false;
    }
}
