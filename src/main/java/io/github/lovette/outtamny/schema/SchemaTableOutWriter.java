package io.github.lovette.outtamny.schema;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.FilterWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * Handles writing output to a specific file for table schema and data.
 * Ensures that the file is created if it doesn't exist and appends to it if it
 * does.
 */
class SchemaTableOutWriter {
    final private java.util.Set<String> tableNames = new java.util.LinkedHashSet<>();
    final private java.util.Set<String> contentHints = new java.util.LinkedHashSet<>();
    private final Path writerPath; // absolute path or "-" for stdout
    private boolean writerAppend = false;// first writer creates file, subsequent writers append
    private boolean needCommit = false;
    private boolean needFooter = false;
    boolean transactionWritten = false;
    private boolean nestedTransactionWritten = false; // track nested writers
    private boolean isStdout;

    // Wrap 'PrintWriter' to prevent "try-with-resources" from closing the output
    // stream.
    static class PrintWriterIgnoreClose extends FilterWriter {
        public PrintWriterIgnoreClose(PrintWriter out) {
            super(out);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }

    static Writer stdoutWriter; // set with setStdoutWriter()

    /**
     * Constructs a TableOutWriter.
     *
     * @param outPath the absolute path to the output file or "-" for stdout
     */
    public SchemaTableOutWriter(Path outPath) {
        if (outPath == null) {
            String err = "TableOutWriter() requires 'writerPath' to be set.";
            throw new IllegalArgumentException(err);
        }

        this.writerPath = outPath;
        this.isStdout = "-".equals(outPath.toString());

        if (!(isStdout || this.writerPath.isAbsolute())) {
            String err = "TableOutWriter() requires 'writerPath' to be absolute or '-' for stdout.";
            throw new IllegalArgumentException(err);
        }
    }

    /**
     * Sets the writer to use for standard output.
     *
     * @param pw the PrintWriter to use for stdout
     */
    static public void setStdoutWriter(PrintWriter pw) {
        stdoutWriter = new PrintWriterIgnoreClose(pw);
    }

    /**
     * Relativizes the writer path against the given path.
     *
     * @param otherPath the path to relativize against
     * @return the relative path from 'rel' to the writer path
     */
    public Path relativizeWriterPath(Path otherPath) {
        if (!otherPath.isAbsolute()) {
            throw new IllegalArgumentException("relativizeWriterPath() requires an absolute 'otherPath'.");
        }

        return otherPath.relativize(this.writerPath);
    }

    /**
     * Associates a table and content hint with this writer.
     *
     * @param name the name of the table to add
     * @param type the type of content (e.g., "schema", "index", "data")
     */
    public void addTable(String name, String type) {
        if (name == null || name.isEmpty() || type == null || type.isEmpty()) {
            throw new IllegalArgumentException("addTable() requires non-null, non-empty arguments.");
        }

        this.tableNames.add(name);
        this.contentHints.add(name + "." + type);
    }

    /**
     * Adds all tables and content hints from the given TableOutWriter to this
     * writer. Also updates the transactionWritten flag if the given writer has
     * written a transaction.
     *
     * @param tow the TableOutWriter to merge into this writer
     */
    public void addSubWriter(SchemaTableOutWriter tow) {
        if (tow == null) {
            throw new IllegalArgumentException("addSubWriter() requires a non-null TableOutWriter.");
        }

        this.tableNames.addAll(tow.tableNames);
        this.contentHints.addAll(tow.contentHints);
        this.nestedTransactionWritten |= tow.transactionWritten;
    }

    /**
     * Returns whether there is at most one table associated with this writer.
     *
     * @return true if there is at most one table, false otherwise
     */
    public boolean isSingleTable() {
        return this.tableNames.size() <= 1;
    }

    /**
     * Returns whether there is at most one content type associated with this
     * writer. Writer can have a single table but multiple content types (e.g.,
     * "schema", "index", "data").
     *
     * @return true if there is at most one content hint, false otherwise
     */
    public boolean isSingleTableContent() {
        return this.contentHints.size() <= 1;
    }

    /**
     * Returns a Writer for writing to the output file.
     * The first call creates the file, subsequent calls append to it.
     *
     * @return a Writer for the output file
     * @throws IOException if an I/O error occurs while opening the file
     */
    public Writer getWriter() throws IOException {
        if (stdoutWriter == null) {
            throw new IllegalArgumentException("getWriter() requires a non-null stdout writer.");
        }

        if (isStdout) {
            return new BufferedWriter(stdoutWriter);
        }

        boolean append = writerAppend;
        writerAppend = true; // next writer will append

        // stdOut().println((append ? "Appending to " : "Writing to ") + writerPath);

        return new BufferedWriter(
                new FileWriter(writerPath.toString(), StandardCharsets.UTF_8, append));
    }

    /**
     * Writes the given string to the output file.
     * If the string is empty, nothing is written.
     *
     * @param s the string to write to the output file
     * @throws IOException if an I/O error occurs while writing to the file
     */
    public void writeString(String s) throws IOException {
        if (!s.isEmpty()) {
            try (Writer w = getWriter()) {
                w.write(s);
            }
        }
    }

    /**
     * Writes the contents of the given StringWriter to the output file.
     * If the StringWriter is empty, nothing is written.
     *
     * @param sw the StringWriter containing the data to write
     * @throws IOException if an I/O error occurs while writing to the file
     */
    public void writeString(StringWriter sw) throws IOException {
        this.writeString(sw.toString());
    }

    /**
     * Writes a CREATE TABLE statement for the specified table to the output file.
     * Open a new transaction if necessary before writing the statement.
     *
     * @param to the TableOut object representing the table to write
     * @param s  the CREATE TABLE statement to write
     * @throws IOException if an I/O error occurs while writing to the file
     */
    public void writeCreateTable(SchemaTableOut to, String s) throws IOException {
        if (!s.isEmpty()) {
            beginTransaction();
            writeString(s + "\n");
            to.schemaWritten = true;
        }
    }

    /**
     * Writes a CREATE INDEX statement for the specified table to the output file.
     * Open a new transaction if necessary before writing the statement.
     *
     * @param to the TableOut object representing the table to write
     * @param s  the CREATE INDEX statement to write
     * @throws IOException if an I/O error occurs while writing to the file
     */
    public void writeCreateIndex(SchemaTableOut to, String s) throws IOException {
        if (!s.isEmpty()) {
            beginTransaction();
            writeString(s + "\n");
            to.indexWritten = true;
        }
    }

    /**
     * Writes an INSERT INTO statement for the specified table to the output file.
     * Open a new transaction if necessary before writing the statement.
     *
     * @param to the TableOut object representing the table to write
     * @param s  the INSERT INTO statement to write
     * @throws IOException if an I/O error occurs while writing to the file
     */
    public void writeInsertTable(SchemaTableOut to, String s) throws IOException {
        if (!s.isEmpty()) {
            beginTransaction();
            writeString(s + "\n");
            to.dataWritten = true;
        }
    }

    /**
     * Writes a block of paths to the output file, each prefixed with the ".read"
     * command. Opens a new transaction if necessary before writing the paths.
     *
     * @param relPaths the list of relative paths to include in the block
     * @throws IOException if an I/O error occurs while writing to the file
     */
    public void writePathsBlock(List<Path> relPaths) throws IOException {
        if (relPaths.isEmpty())
            return;

        StringWriter sw = new StringWriter();
        for (Path relPath : relPaths) {
            sw.write(".read \"" + relPath + "\"\n");
        }
        sw.write("\n");

        beginTransaction();
        writeString(sw);
        commitTransaction();
    }

    /**
     * Writes the header section for the output file, including disabling foreign
     * keys.
     *
     * @throws IOException if an I/O error occurs while writing to the file
     */
    public void writeHeader() throws IOException {
        StringWriter sw = new StringWriter();
        sw.write("-- Disable foreign keys until the end.\n");
        sw.write("PRAGMA foreign_keys=OFF;\n");
        sw.write("\n");

        writeString(sw);
        needFooter = true;
    }

    /**
     * Writes the footer section for the output file, including enabling foreign
     * keys and performing integrity checks. If a transaction is in progress, it
     * will be committed first.
     *
     * @throws IOException if an I/O error occurs while writing to the file
     */
    public void writeFooter() throws IOException {
        StringWriter sw = new StringWriter();
        sw.write("-- Sanity check foreign keys, etc.\n");
        sw.write("PRAGMA foreign_keys=ON;\n");
        sw.write("PRAGMA foreign_key_check;\n");
        sw.write("PRAGMA integrity_check;\n");
        sw.write("PRAGMA optimize;\n");

        forceCommitTransaction();

        if (needFooter) {
            writeString(sw);
            needFooter = false;
        }

        getWriter().flush();
    }

    /**
     * Begins a new transaction for the output file if necessary.
     * Safe to call multiple times; will only begin a transaction if one is not
     * already in progress.
     *
     * @throws IOException if an I/O error occurs while writing to the file
     */
    public void beginTransaction() throws IOException {
        if (isSingleTableContent()) {
            return; // No transactions for single content files
        }
        if (nestedTransactionWritten) {
            return; // sqlite does not support nested transactions
        }

        if (!needCommit) {
            writeString("BEGIN TRANSACTION;\n\n");
            needCommit = true;
            transactionWritten = true;
        }
    }

    /**
     * Commits the current transaction for the output file if necessary.
     *
     * @throws IOException if an I/O error occurs while writing to the file
     */
    public void commitTransaction() throws IOException {
        if (isSingleTable()) {
            return; // Transaction is closed at the end of single table files
        }

        forceCommitTransaction();
    }

    /**
     * Commits the current transaction for the output file if necessary.
     *
     * @throws IOException if an I/O error occurs while writing to the file
     */
    public void forceCommitTransaction() throws IOException {
        if (needCommit) {
            writeString("COMMIT;\n\n");
            needCommit = false;
        }
    }
}
