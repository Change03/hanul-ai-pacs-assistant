# Threat Model

## Boundaries

- Synthetic data only
- Local demo only
- No clinical use
- Basic demo session authentication

## Key Risks

- PHI upload
- External provider misuse
- Weak local credentials
- Audit tampering
- Unsupported DICOM parsing

## Current Mitigations

- Prominent demo-only disclaimers
- QC gate with privacy checks
- Synthetic seed data
- Anthropic provider marked experimental and disabled by default
- No real data policy
- Audit log for DICOM access, QC, AI, STOW, and read-back events

## Future Hardening

- OAuth2/OIDC
- TLS everywhere
- Immutable audit logs
- RBAC/ABAC
- Object storage encryption
- Network segmentation
