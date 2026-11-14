# outtamny — Microsoft Money data export

[![Test and release package](https://github.com/lovette/outtamny/actions/workflows/release.yml/badge.svg)](https://github.com/lovette/outtamny/actions/workflows/release.yml)

`outtamny` is a Java command-line utility that reads a Microsoft Money `.mny` file and generates SQL to recreate its tables, indexes, and data in SQLite.

## Key Features

- Extracts table schemas, indexes, and records from Microsoft Money files.
- Supports password protected Money files.
- Writes output to a SQL file, a directory, or standard output.
- Generates SQLite-compatible SQL in various file layouts.
- Lets you select how money values are represented.

## Getting Started

The quickest way to get started is to download the [latest release](https://github.com/lovette/outtamny/releases) JAR file from the repo. Java 25 or later is required.

```bash
curl -L -O https://github.com/lovette/outtamny/releases/latest/download/outtamny.jar
```

The `schema` command generates all the SQL necessary to recreate table schemas, indexes and data from a Microsoft Money file in SQLite. Creating a SQLite database is as simple as:

```bash
java -jar outtamny.jar schema --out - src/test/resources/sunset-sample.mny | sqlite3 sample.db
```

## Main command

```bash
% java -jar outtamny.jar --help

Usage: outtamny [-hVv] [COMMAND]

Gets your financial data outta a Microsoft Money data file.

  -h, --help      Show this help message and exit.
  -v, --verbose   Increase verbose level.
  -V, --version   Print version information and exit.

Commands:
  schema  Create SQL to import table schemas and data into SQLite.
```

## Command `schema`

```bash
% java -jar outtamny.jar schema --help

Usage: outtamny schema [-hv] [-p] [--[no-]skip-empty] [-d=<dataOutput>]
                       [--money-type=<moneyType>] [-o=DIRECTORY|FILE|-]
                       [--password:env=ENVVAR] [--password:file=FILE]
                       [--password:pw=PASSWORD] [-s=<schemaOutput>] MNYFILE

Output SQL that creates Money tables, indexes and data in SQLite.

      MNYFILE                     Money data file (.mny).
  -v, --verbose                   Increase verbose level.
  -o, --out=DIRECTORY|FILE|-      The name for combined schema file, the directory
                                  to write , or '-' for stdout. Defaults to
                                  MNYFILE's name and directory.
  -p, --password                  Prompt for the password for the Money data file.
      --password:pw=PASSWORD      The password for the Money data file
                                  (may be visible in shell history).
      --password:file=FILE        The file containing the password for the
                                  Money data file.
      --password:env=ENVVAR       The environment variable containing the password
                                  for the Money data file.
  -s, --schema-out=<schemaOutput> Structure schema output format.
                                  Valid values: combined, split, table_combined,
                                  table, none
  -d, --data-out=<dataOutput>     Data output format.
                                  Valid values: combined, split, table_combined,
                                  table, none
      --money-type=<moneyType>    Data type to store money values as.
                                  Valid values: numeric, text, integer
      --[no-]skip-empty           Skip empty tables.
  -h, --help                      Display this help and exit
```

### Generating SQL

The `schema` command requires at minimum the Money data file path. By default, output is written as an SQL file containing the schema and data for all tables, written alongside the Money data file, using the same filename with a `.sql` extension. The `--out` option accepts a destination directory, a file path to write the combined SQL output, or `-` for standard output. The destination directory must already exist.

```bash
java -jar outtamny.jar schema ./data/finance.mny
```

The output layout is set with `--schema-out` and `--data-out` options. Besides a single file with all schema SQL, you can split schema, indexes, and data into separate files or organize them per table. Using `none` for one option allows you to omit that category from the output entirely.

```bash
java -jar outtamny.jar schema --schema-out split --data-out none --out ./export ./data/finance.mny
```

### Money Column Types

SQLite does not have a natively supported fixed decimal [column data type](https://www.sqlite.org/datatype3.html) that can store and manipulate money values accurately. The `--money-type` option allows you to choose to store money values as `numeric` (default), `text`, or `integer`. This allows the database to store money values in a format that preserves decimal accuracy and suits your application's needs.
Output layouts for schema and data are:

| Value | Output |
| --- | --- |
| `combined` | Combined SQL output (default) |
| `split` | Separate schema, index, and data output |
| `table_combined` | One combined SQL file per table |
| `table` | Separate schema, index, and data output per table |
| `none` | Do not write that output category |

## Command `tables`

```bash
% java -jar outtamny.jar tables --help

Usage: outtamny tables [-hv] [-p] [--password:env=ENVVAR] [--password:
                       file=FILE] [--password:pw=PASSWORD] MNYFILE

List Money database table names.

      MNYFILE                Money data file (.mny).
  -p, --password             Prompt for the password for the Money data file.
      --password:pw=PASSWORD The password for the Money data file (may be
                             visible in shell history).
      --password:file=FILE   The file containing the password for the Money
                             data file.
      --password:env=ENVVAR  The environment variable containing the password
                             for the Money data file.
  -v, --verbose              Increase verbose level.
  -h, --help                 Display this help and exit
```

## Password Protection

Money data files can be protected by a password. There are various ways to provide the password to `outtamny`.

Use `-p` / `--password` to be prompted to enter the password:

```bash
java -jar outtamny.jar schema --password ./data/finance.mny
```

Alternatively, read the password from an environment variable:

```bash
java -jar outtamny.jar schema --password:env MNYPASSWORD ./data/finance.mny
```

Or file:

```bash
java -jar outtamny.jar schema --password:file ./secrets/mny-password.txt ./data/finance.mny
```

The final option is `--password:pw PASSWORD` which accepts a literal password on the command line. This is the least secure since it may be visible in shell history.

**Data exported to SQLite will no longer be protected by a password.** Standard public-domain SQLite does not include built-in encryption. Because SQLite stores the entire database in a single plain-text file, anyone with read access to that file can view its contents unless you use a specialized extension, a custom fork, or operating-system-level encryption.

## Development

`outtamny` is compatible with **Java 25** or later. It uses **Jackcess** for reading Microsoft Money data files. **Maven** is required for building and testing. The `sqlite3` command-line tool is required to run the SQLite integration tests.

Compile, test and package a JAR containing the application and dependencies:

```bash
mvn package
```

To run the tests separately:

```bash
mvn test
```

## License

This project is distributed under the BSD 3-Clause License. See [LICENSE](LICENSE) for the full text.
