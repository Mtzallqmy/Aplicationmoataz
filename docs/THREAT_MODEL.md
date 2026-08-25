# Threat model

| Threat | Existing control | Remaining work |
| --- | --- | --- |
| Malicious project instructions | Fixed system prompt treats repository text as untrusted | Structured context provenance |
| Workspace traversal | Canonical workspace root and parent-path checks | Descriptor-relative atomic file operations |
| Escaping symbolic links | Existing symlink ancestors are verified | Race-resistant external-storage handling |
| Sensitive file disclosure | Sensitive names are rejected by file tools | User-mediated secret read flow |
| Destructive shell commands | Pattern-informed risk analysis and explicit approvals | OS-enforced isolation and shell parsing |
| Package-install supply chain | Package-manager commands require approval | Dependency provenance and network controls |
| Provider key disclosure | Android Keystore AES-GCM encryption | Redacted diagnostics and hardware-backed policy reporting |
| Archive traversal | Normalized destination checks | Extraction quotas and hardlink/symlink sequencing hardening |
| Unauthorized Telegram use | Explicit user allowlist and optional chat allowlist | Rate limiting, foreground lifecycle, approval routing |
| Malicious MCP server | Disabled/untrusted by default and explicit execution trust | Per-tool grants, stdio isolation, signed registration |
| Provider data exfiltration | Explicit provider setup and direct transport | Granular context/privacy controls |
| Android process death | Persisted session/message data | Durable task recovery and truthful interrupted-state UI |
| Web preview attacks | Web preview is not implemented | Hardened origin-specific preview if added |
