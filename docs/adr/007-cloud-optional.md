# ADR 007: Hosted services remain optional

Status: accepted.

Do not install Cloudflare, Supabase, Firebase, or proprietary cloud SDKs in the
core modules. Future sync, Telegram webhooks, device relay, and artifact storage
must be independently optional and preserve local-only operation.
