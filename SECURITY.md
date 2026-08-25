# Security model

## Trust boundaries

- System and user instructions are trusted independently of repository files.
- Repository content, downloaded content, provider output, command output, and
  MCP tool results are untrusted.
- Every file tool resolves paths relative to one selected workspace.
- Absolute paths, traversal, escaping symbolic links, and known sensitive
  filenames are rejected.
- Shell commands are evaluated for secret access, destructive operations,
  package installation, network access, parent traversal, and Git mutations.
- Critical actions never receive blanket session approval.
- Plan mode cannot mutate files. Ask mode cannot run tools.
- Telegram access is denied until at least one explicit user ID is configured.
- MCP execution requires an enabled server and explicit user trust.

## Credentials

API keys are encrypted with AES-GCM using a non-exportable Android Keystore
key. SQLite stores only opaque secret references. Provider keys are never
intentionally included in logs, exported configuration, or Telegram messages.

## Known caveats

Android application processes do not provide an OS-enforced chroot around the
native shell. A workspace working directory and command analysis are controls,
not equivalent to kernel sandboxing or a verified PRoot jail.

Path validation before opening files can have time-of-check/time-of-use races
in adversarial shared directories. Application-private workspaces reduce that
risk, but descriptor-relative secure opening should be added before supporting
untrusted writable external mounts.

Archive extraction must also enforce quotas and harden symbolic-link handling
before accepting arbitrary remote distributions in production.

Do not describe this early implementation as a formally verified sandbox.
