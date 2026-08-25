# ADR 005: Direct OpenAI-compatible provider abstraction

Status: accepted.

Use one application-independent provider interface and a real OpenAI-compatible
SSE adapter. Provider keys remain in Android Keystore, while the model ID is
user-configurable. Avoid provider-specific domain types and a mandatory proxy.
