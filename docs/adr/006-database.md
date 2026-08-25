# ADR 006: SQLiteOpenHelper instead of generated Room models

Status: accepted.

Use Android's built-in SQLiteOpenHelper for the initial storage implementation.
This provides real SQLite persistence and explicit schema control without
adding KSP code generation to a build environment that cannot currently resolve
or compile dependencies. Destructive production migration is prohibited.
