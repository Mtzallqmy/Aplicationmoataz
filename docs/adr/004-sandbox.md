# ADR 004: Distinguish process shell, PTY, and PRoot

Status: accepted.

The initial shell starts actual operating-system processes but is not a PTY.
Rootfs installation and PRoot invocation are separate capabilities. Do not
bundle GPL-covered upstream artifacts without a deliberate licensing decision,
and do not present any unverified PRoot execution as a completed sandbox.
